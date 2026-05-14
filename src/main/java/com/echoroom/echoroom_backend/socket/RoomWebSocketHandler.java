package com.echoroom.echoroom_backend.socket;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.echoroom.echoroom_backend.dto.ChatBroadcast;
import com.echoroom.echoroom_backend.dto.ChatMessage;
import com.echoroom.echoroom_backend.dto.JoinRoomRequest;
import com.echoroom.echoroom_backend.dto.RealtimeMessage;
import com.echoroom.echoroom_backend.dto.RealtimeRequest;
import com.echoroom.echoroom_backend.dto.RequestControlMessage;
import com.echoroom.echoroom_backend.dto.RoomUsersUpdate;
import com.echoroom.echoroom_backend.dto.SocketErrorResponse;
import com.echoroom.echoroom_backend.dto.VideoStateMessage;
import com.echoroom.echoroom_backend.service.RoomService;
import com.echoroom.echoroom_backend.service.RoomService.RoomUsersSnapshot;
import com.echoroom.echoroom_backend.service.VideoStateService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

	private static final String JOIN_ROOM = "joinRoom";
	private static final String SEND_MESSAGE = "sendMessage";
	private static final String CHAT_MESSAGE = "chatMessage";
	private static final String ROOM_USERS = "roomUsers";
	private static final String ROOM_ERROR = "roomError";
	private static final String VIDEO_STATE = "videoState";
	private static final String ROOM_VIDEO_STATE = "roomVideoState";
	private static final String REQUEST_CONTROL = "requestControl";
	private static final String CONTROL_STATE = "controlState";

	private final ObjectMapper objectMapper;
	private final RoomService roomService;
	private final VideoStateService videoStateService;
	private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> sessionRooms = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Set<String>> roomSessions = new ConcurrentHashMap<>();

	public RoomWebSocketHandler(ObjectMapper objectMapper, RoomService roomService, VideoStateService videoStateService) {
		this.objectMapper = objectMapper;
		this.roomService = roomService;
		this.videoStateService = videoStateService;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.put(session.getId(), session);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		try {
			RealtimeRequest request = objectMapper.readValue(message.getPayload(), RealtimeRequest.class);
			if (request == null || request.event() == null) {
				sendError(session, "Invalid realtime message");
				return;
			}
			switch (request.event()) {
				case JOIN_ROOM -> joinRoom(session, toValue(request, JoinRoomRequest.class));
				case SEND_MESSAGE -> sendMessage(session, toValue(request, ChatMessage.class));
				case VIDEO_STATE -> syncVideoState(session, toValue(request, VideoStateMessage.class));
				case REQUEST_CONTROL -> requestControl(session, toValue(request, RequestControlMessage.class));
				default -> sendError(session, "Unknown event: " + request.event());
			}
		} catch (JacksonException ex) {
			sendError(session, "Invalid JSON message");
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session.getId());
		disconnect(session.getId());
	}

	private <T> T toValue(RealtimeRequest request, Class<T> type) throws JacksonException {
		if (request.data() == null || request.data().isNull() || request.data().isMissingNode()) {
			return null;
		}
		return objectMapper.treeToValue(request.data(), type);
	}

	private void joinRoom(WebSocketSession session, JoinRoomRequest request) {
		if (request == null) {
			sendError(session, "Invalid join request");
			return;
		}

		String roomId = roomService.normalizeRoomId(request.roomId()).orElse(null);
		String name = roomService.normalizeName(request.name()).orElse(null);
		if (roomId == null) {
			sendError(session, "roomId is required");
			return;
		}
		if (name == null) {
			sendError(session, "name is required");
			return;
		}
		String senderClientId = normalizeClientId(request.senderClientId());
		if (senderClientId == null) {
			sendError(session, "senderClientId is required");
			return;
		}
		if (!roomService.roomExists(roomId)) {
			sendError(session, "Room not found");
			return;
		}

		leaveTrackedRoom(session.getId());
		roomService.addUserToRoom(roomId, session.getId(), name);
		sessionRooms.put(session.getId(), roomId);
		roomSessions.computeIfAbsent(roomId, ignored -> ConcurrentHashMap.newKeySet()).add(session.getId());
		broadcastUsers(roomId);
		send(session, ROOM_VIDEO_STATE, videoStateService.getStateOrDefault(roomId));
		boolean hadController = videoStateService.getControlState(roomId).isPresent();
		videoStateService.ensureController(roomId, name, senderClientId)
			.ifPresent(controlState -> {
				if (hadController) {
					send(session, CONTROL_STATE, controlState);
				} else {
					broadcast(roomId, CONTROL_STATE, controlState, null);
				}
			});
	}

	private void sendMessage(WebSocketSession session, ChatMessage message) {
		if (message == null || message.content() == null || message.content().isBlank()) {
			sendError(session, "Message content is required");
			return;
		}

		String roomId = roomService.normalizeRoomId(message.roomId()).orElse(null);
		String sender = roomService.normalizeName(message.sender()).orElse(null);
		if (roomId == null || sender == null || !roomService.roomExists(roomId)) {
			sendError(session, "Invalid room or sender");
			return;
		}

		broadcast(roomId, CHAT_MESSAGE, new ChatBroadcast(sender, message.content().trim()), null);
	}

	private void syncVideoState(WebSocketSession session, VideoStateMessage message) {
		if (message == null) {
			sendError(session, "Invalid video state");
			return;
		}

		String roomId = roomService.normalizeRoomId(message.roomId()).orElse(null);
		if (roomId == null || !roomService.roomExists(roomId)) {
			sendError(session, "Room not found");
			return;
		}
		if (!videoStateService.isController(roomId, message.senderClientId())) {
			sendError(session, "Only the room controller can update video state");
			return;
		}

		videoStateService.store(roomId, message)
			.ifPresentOrElse(
				state -> broadcast(roomId, VIDEO_STATE, state, session.getId()),
				() -> sendError(session, "Invalid video state"));
	}

	private void requestControl(WebSocketSession session, RequestControlMessage message) {
		if (message == null) {
			sendError(session, "Invalid control request");
			return;
		}

		String roomId = roomService.normalizeRoomId(message.roomId()).orElse(null);
		if (roomId == null || !roomService.roomExists(roomId)) {
			sendError(session, "Room not found");
			return;
		}

		videoStateService.requestControl(roomId, message.name(), message.senderClientId())
			.ifPresentOrElse(
				controlState -> broadcast(roomId, CONTROL_STATE, controlState, null),
				() -> sendError(session, "Invalid control request"));
	}

	private void disconnect(String sessionId) {
		String roomId = leaveTrackedRoom(sessionId);
		RoomUsersSnapshot snapshot = roomService.removeUser(sessionId);
		String roomToUpdate = snapshot.roomId() != null ? snapshot.roomId() : roomId;
		if (roomToUpdate != null) {
			broadcastUsers(roomToUpdate);
		}
	}

	private String leaveTrackedRoom(String sessionId) {
		String roomId = sessionRooms.remove(sessionId);
		if (roomId != null) {
			Set<String> members = roomSessions.get(roomId);
			if (members != null) {
				members.remove(sessionId);
				if (members.isEmpty()) {
					roomSessions.remove(roomId, members);
				}
			}
		}
		return roomId;
	}

	private void broadcastUsers(String roomId) {
		var users = roomService.getRoomUsers(roomId);
		broadcast(roomId, ROOM_USERS, new RoomUsersUpdate(users, users.size()), null);
	}

	private void broadcast(String roomId, String event, Object data, String excludedSessionId) {
		Set<String> members = roomSessions.get(roomId);
		if (members == null) {
			return;
		}
		for (String sessionId : members) {
			if (sessionId.equals(excludedSessionId)) {
				continue;
			}
			WebSocketSession session = sessions.get(sessionId);
			if (session != null) {
				send(session, event, data);
			}
		}
	}

	private void sendError(WebSocketSession session, String message) {
		send(session, ROOM_ERROR, new SocketErrorResponse(message));
	}

	private void send(WebSocketSession session, String event, Object data) {
		if (!session.isOpen()) {
			return;
		}
		try {
			String payload = objectMapper.writeValueAsString(new RealtimeMessage(event, data));
			synchronized (session) {
				if (session.isOpen()) {
					session.sendMessage(new TextMessage(payload));
				}
			}
		} catch (JacksonException | IOException ignored) {
		}
	}

	private String normalizeClientId(String senderClientId) {
		if (senderClientId == null || senderClientId.isBlank()) {
			return null;
		}
		return senderClientId.trim();
	}
}
