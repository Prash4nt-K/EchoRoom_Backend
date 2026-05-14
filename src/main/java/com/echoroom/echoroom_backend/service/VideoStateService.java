package com.echoroom.echoroom_backend.service;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.echoroom.echoroom_backend.dto.ControlStateMessage;
import com.echoroom.echoroom_backend.dto.VideoStateMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VideoStateService {

	private static final String DEFAULT_VIDEO_ID = "dQw4w9WgXcQ";
	private static final Set<String> VALID_TYPES = Set.of("change", "play", "pause", "seek", "state");

	private final ConcurrentMap<String, RoomVideoControlState> roomStates = new ConcurrentHashMap<>();
	private final RoomService roomService;
	private final Clock clock;

	@Autowired
	public VideoStateService(RoomService roomService) {
		this(roomService, Clock.systemUTC());
	}

	VideoStateService(RoomService roomService, Clock clock) {
		this.roomService = roomService;
		this.clock = clock;
	}

	public Optional<VideoStateMessage> store(String roomId, VideoStateMessage message) {
		String normalizedRoomId = roomService.normalizeRoomId(roomId).orElse(null);
		if (normalizedRoomId == null || message == null || !roomService.roomExists(normalizedRoomId)
			|| !isValidType(message.type()) || message.videoId() == null || message.videoId().isBlank()
			|| !isController(normalizedRoomId, message.senderClientId())) {
			return Optional.empty();
		}

		VideoStateMessage storedState = new VideoStateMessage(
			normalizedRoomId,
			roomService.normalizeName(message.sender()).orElse(""),
			normalizeText(message.senderClientId()),
			message.type(),
			message.videoId().trim(),
			Math.max(0, message.currentTime()),
			message.isPlaying(),
			message.updatedAt() > 0 ? message.updatedAt() : now());
		roomStates.compute(normalizedRoomId, (key, current) -> {
			RoomVideoControlState state = current != null ? current : new RoomVideoControlState();
			state.videoState = storedState;
			return state;
		});
		return Optional.of(adjustForPlayback(storedState));
	}

	public VideoStateMessage getStateOrDefault(String roomId) {
		String normalizedRoomId = roomService.normalizeRoomId(roomId).orElse(null);
		if (normalizedRoomId == null) {
			return defaultState(null);
		}
		RoomVideoControlState roomState = roomStates.get(normalizedRoomId);
		if (roomState == null || roomState.videoState == null) {
			return defaultState(normalizedRoomId);
		}
		return adjustForPlayback(roomState.videoState);
	}

	public Optional<ControlStateMessage> ensureController(String roomId, String name, String senderClientId) {
		String normalizedRoomId = roomService.normalizeRoomId(roomId).orElse(null);
		String controller = roomService.normalizeName(name).orElse(null);
		String controllerClientId = normalizeRequiredText(senderClientId).orElse(null);
		if (normalizedRoomId == null || controller == null || controllerClientId == null
			|| !roomService.roomExists(normalizedRoomId)) {
			return Optional.empty();
		}

		RoomVideoControlState state = roomStates.compute(normalizedRoomId, (key, current) -> {
			RoomVideoControlState existing = current != null ? current : new RoomVideoControlState();
			if (existing.controlState == null) {
				existing.controlState = new ControlStateMessage(controller, controllerClientId);
			}
			return existing;
		});
		return Optional.of(state.controlState);
	}

	public Optional<ControlStateMessage> requestControl(String roomId, String name, String senderClientId) {
		String normalizedRoomId = roomService.normalizeRoomId(roomId).orElse(null);
		String controller = roomService.normalizeName(name).orElse(null);
		String controllerClientId = normalizeRequiredText(senderClientId).orElse(null);
		if (normalizedRoomId == null || controller == null || controllerClientId == null
			|| !roomService.roomExists(normalizedRoomId)) {
			return Optional.empty();
		}

		ControlStateMessage controlState = new ControlStateMessage(controller, controllerClientId);
		roomStates.compute(normalizedRoomId, (key, current) -> {
			RoomVideoControlState state = current != null ? current : new RoomVideoControlState();
			state.controlState = controlState;
			return state;
		});
		return Optional.of(controlState);
	}

	public Optional<ControlStateMessage> getControlState(String roomId) {
		String normalizedRoomId = roomService.normalizeRoomId(roomId).orElse(null);
		if (normalizedRoomId == null) {
			return Optional.empty();
		}
		RoomVideoControlState state = roomStates.get(normalizedRoomId);
		return state == null ? Optional.empty() : Optional.ofNullable(state.controlState);
	}

	public boolean isController(String roomId, String senderClientId) {
		String normalizedRoomId = roomService.normalizeRoomId(roomId).orElse(null);
		String normalizedClientId = normalizeRequiredText(senderClientId).orElse(null);
		if (normalizedRoomId == null || normalizedClientId == null) {
			return false;
		}
		return getControlState(normalizedRoomId)
			.map(controlState -> normalizedClientId.equals(controlState.controllerClientId()))
			.orElse(false);
	}

	private VideoStateMessage adjustForPlayback(VideoStateMessage state) {
		long now = now();
		if (!state.isPlaying()) {
			return state;
		}
		double adjustedTime = state.currentTime() + ((now - state.updatedAt()) / 1000.0);
		return new VideoStateMessage(
			state.roomId(),
			state.sender(),
			state.senderClientId(),
			state.type(),
			state.videoId(),
			Math.max(0, adjustedTime),
			true,
			now);
	}

	private VideoStateMessage defaultState(String roomId) {
		return new VideoStateMessage(roomId, "", "", "state", DEFAULT_VIDEO_ID, 0, false, now());
	}

	private boolean isValidType(String type) {
		return type != null && VALID_TYPES.contains(type);
	}

	private String normalizeText(String value) {
		return value == null ? "" : value.trim();
	}

	private Optional<String> normalizeRequiredText(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(value.trim());
	}

	private long now() {
		return clock.millis();
	}

	private static final class RoomVideoControlState {
		private ControlStateMessage controlState;
		private VideoStateMessage videoState;
	}
}
