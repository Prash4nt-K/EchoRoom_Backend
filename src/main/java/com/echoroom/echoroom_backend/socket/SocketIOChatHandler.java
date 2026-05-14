package com.echoroom.echoroom_backend.socket;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.echoroom.echoroom_backend.dto.ChatBroadcast;
import com.echoroom.echoroom_backend.dto.ChatMessage;
import com.echoroom.echoroom_backend.dto.JoinRoomRequest;
import com.echoroom.echoroom_backend.dto.RequestControlMessage;
import com.echoroom.echoroom_backend.dto.RoomUsersUpdate;
import com.echoroom.echoroom_backend.dto.SocketErrorResponse;
import com.echoroom.echoroom_backend.dto.VideoStateMessage;
import com.echoroom.echoroom_backend.service.RoomService;
import com.echoroom.echoroom_backend.service.RoomService.RoomUsersSnapshot;
import com.echoroom.echoroom_backend.service.VideoStateService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class SocketIOChatHandler {

	private static final String JOIN_ROOM = "joinRoom";
	private static final String SEND_MESSAGE = "sendMessage";
	private static final String CHAT_MESSAGE = "chatMessage";
	private static final String ROOM_USERS = "roomUsers";
	private static final String ROOM_ERROR = "roomError";
	private static final String VIDEO_STATE = "videoState";
	private static final String ROOM_VIDEO_STATE = "roomVideoState";
	private static final String REQUEST_CONTROL = "requestControl";
	private static final String CONTROL_STATE = "controlState";

	private final SocketIOServer server;
	private final RoomService roomService;
	private final VideoStateService videoStateService;

	public SocketIOChatHandler(SocketIOServer server, RoomService roomService, VideoStateService videoStateService) {
		this.server = server;
		this.roomService = roomService;
		this.videoStateService = videoStateService;
	}

	@PostConstruct
	void registerListeners() {
		server.addEventListener(JOIN_ROOM, JoinRoomRequest.class, (client, request, ackSender) -> joinRoom(client, request));
		server.addEventListener(SEND_MESSAGE, ChatMessage.class, (client, message, ackSender) -> sendMessage(client, message));
		server.addEventListener(VIDEO_STATE, VideoStateMessage.class, (client, message, ackSender) -> syncVideoState(client, message));
		server.addEventListener(REQUEST_CONTROL, RequestControlMessage.class, (client, message, ackSender) -> requestControl(client, message));
		server.addDisconnectListener(this::disconnect);
	}

	private void joinRoom(SocketIOClient client, JoinRoomRequest request) {
		if (request == null) {
			sendError(client, "Invalid join request");
			return;
		}

		String roomId = roomService.normalizeRoomId(request.roomId()).orElse(null);
		String name = roomService.normalizeName(request.name()).orElse(null);
		if (roomId == null) {
			sendError(client, "roomId is required");
			return;
		}
		if (name == null) {
			sendError(client, "name is required");
			return;
		}
		String senderClientId = normalizeClientId(request.senderClientId()).orElse(null);
		if (senderClientId == null) {
			sendError(client, "senderClientId is required");
			return;
		}
		if (!roomService.roomExists(roomId)) {
			sendError(client, "Room not found");
			return;
		}

		roomService.addUserToRoom(roomId, client.getSessionId().toString(), name);
		client.joinRoom(roomId);
		broadcastUsers(roomId);
		client.sendEvent(ROOM_VIDEO_STATE, videoStateService.getStateOrDefault(roomId));
		boolean hadController = videoStateService.getControlState(roomId).isPresent();
		videoStateService.ensureController(roomId, name, senderClientId)
			.ifPresent(controlState -> {
				if (hadController) {
					client.sendEvent(CONTROL_STATE, controlState);
				} else {
					server.getRoomOperations(roomId).sendEvent(CONTROL_STATE, controlState);
				}
			});
	}

	private void sendMessage(SocketIOClient client, ChatMessage message) {
		if (message == null || message.content() == null || message.content().isBlank()) {
			sendError(client, "Message content is required");
			return;
		}

		String roomId = roomService.normalizeRoomId(message.roomId()).orElse(null);
		String sender = roomService.normalizeName(message.sender()).orElse(null);
		if (roomId == null || sender == null || !roomService.roomExists(roomId)) {
			sendError(client, "Invalid room or sender");
			return;
		}

		server.getRoomOperations(roomId)
			.sendEvent(CHAT_MESSAGE, new ChatBroadcast(sender, message.content().trim()));
	}

	private void syncVideoState(SocketIOClient client, VideoStateMessage message) {
		if (message == null) {
			sendError(client, "Invalid video state");
			return;
		}

		String roomId = roomService.normalizeRoomId(message.roomId()).orElse(null);
		if (roomId == null || !roomService.roomExists(roomId)) {
			sendError(client, "Room not found");
			return;
		}
		if (!videoStateService.isController(roomId, message.senderClientId())) {
			sendError(client, "Only the room controller can update video state");
			return;
		}

		videoStateService.store(roomId, message)
			.ifPresentOrElse(
				state -> server.getRoomOperations(roomId).sendEvent(VIDEO_STATE, client, state),
				() -> sendError(client, "Invalid video state"));
	}

	private void requestControl(SocketIOClient client, RequestControlMessage message) {
		if (message == null) {
			sendError(client, "Invalid control request");
			return;
		}

		String roomId = roomService.normalizeRoomId(message.roomId()).orElse(null);
		if (roomId == null || !roomService.roomExists(roomId)) {
			sendError(client, "Room not found");
			return;
		}

		videoStateService.requestControl(roomId, message.name(), message.senderClientId())
			.ifPresentOrElse(
				controlState -> server.getRoomOperations(roomId).sendEvent(CONTROL_STATE, controlState),
				() -> sendError(client, "Invalid control request"));
	}

	private void disconnect(SocketIOClient client) {
		RoomUsersSnapshot snapshot = roomService.removeUser(client.getSessionId().toString());
		if (snapshot.roomId() != null) {
			client.leaveRoom(snapshot.roomId());
			server.getRoomOperations(snapshot.roomId())
				.sendEvent(ROOM_USERS, new RoomUsersUpdate(snapshot.users(), snapshot.users().size()));
		}
	}

	private void broadcastUsers(String roomId) {
		var users = roomService.getRoomUsers(roomId);
		server.getRoomOperations(roomId)
			.sendEvent(ROOM_USERS, new RoomUsersUpdate(users, users.size()));
	}

	private void sendError(SocketIOClient client, String message) {
		client.sendEvent(ROOM_ERROR, new SocketErrorResponse(message));
	}

	private java.util.Optional<String> normalizeClientId(String senderClientId) {
		if (senderClientId == null || senderClientId.isBlank()) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(senderClientId.trim());
	}
}
