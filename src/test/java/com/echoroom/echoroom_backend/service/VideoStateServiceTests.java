package com.echoroom.echoroom_backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.echoroom.echoroom_backend.dto.VideoStateMessage;
import org.junit.jupiter.api.Test;

class VideoStateServiceTests {

	private final RoomService roomService = new RoomService();

	@Test
	void returnsDefaultStateWhenRoomHasNoVideoState() {
		String roomId = roomService.createRoom("Alice");
		VideoStateService videoStateService = new VideoStateService(roomService, fixedClock(1_000));

		VideoStateMessage state = videoStateService.getStateOrDefault(roomId);

		assertThat(state.roomId()).isEqualTo(roomId);
		assertThat(state.type()).isEqualTo("state");
		assertThat(state.videoId()).isEqualTo("dQw4w9WgXcQ");
		assertThat(state.currentTime()).isZero();
		assertThat(state.isPlaying()).isFalse();
		assertThat(state.updatedAt()).isEqualTo(1_000);
	}

	@Test
	void rejectsStateForMissingRoom() {
		VideoStateService videoStateService = new VideoStateService(roomService, fixedClock(1_000));

		var stored = videoStateService.store("missing", message("missing", "play", 10, true, 1_000));

		assertThat(stored).isEmpty();
	}

	@Test
	void rejectsInvalidVideoStateType() {
		String roomId = roomService.createRoom("Alice");
		VideoStateService videoStateService = new VideoStateService(roomService, fixedClock(1_000));
		videoStateService.ensureController(roomId, "Alice", "client-1");

		var stored = videoStateService.store(roomId, message(roomId, "restart", 10, true, 1_000));

		assertThat(stored).isEmpty();
	}

	@Test
	void firstControllerIsKeptUntilControlIsRequested() {
		String roomId = roomService.createRoom("Alice");
		VideoStateService videoStateService = new VideoStateService(roomService, fixedClock(1_000));

		var first = videoStateService.ensureController(roomId, "Alice", "client-1").orElseThrow();
		var secondJoin = videoStateService.ensureController(roomId, "Bob", "client-2").orElseThrow();

		assertThat(first.controller()).isEqualTo("Alice");
		assertThat(secondJoin.controller()).isEqualTo("Alice");
		assertThat(secondJoin.controllerClientId()).isEqualTo("client-1");
	}

	@Test
	void requestControlChangesController() {
		String roomId = roomService.createRoom("Alice");
		VideoStateService videoStateService = new VideoStateService(roomService, fixedClock(1_000));
		videoStateService.ensureController(roomId, "Alice", "client-1");

		var controlState = videoStateService.requestControl(roomId, "Bob", "client-2").orElseThrow();

		assertThat(controlState.controller()).isEqualTo("Bob");
		assertThat(controlState.controllerClientId()).isEqualTo("client-2");
		assertThat(videoStateService.isController(roomId, "client-2")).isTrue();
	}

	@Test
	void rejectsVideoStateFromNonController() {
		String roomId = roomService.createRoom("Alice");
		VideoStateService videoStateService = new VideoStateService(roomService, fixedClock(1_000));
		videoStateService.ensureController(roomId, "Alice", "client-1");

		var stored = videoStateService.store(roomId,
			new VideoStateMessage(roomId, "Bob", "client-2", "play", "abc123", 10, true, 1_000));

		assertThat(stored).isEmpty();
	}

	@Test
	void adjustsCurrentTimeForPlayingState() {
		String roomId = roomService.createRoom("Alice");
		VideoStateService videoStateService = new VideoStateService(roomService, fixedClock(4_000));
		videoStateService.ensureController(roomId, "Alice", "client-1");

		VideoStateMessage state = videoStateService.store(roomId, message(roomId, "play", 10, true, 1_000))
			.orElseThrow();

		assertThat(state.currentTime()).isEqualTo(13);
		assertThat(state.updatedAt()).isEqualTo(4_000);
	}

	@Test
	void doesNotAdjustPausedState() {
		String roomId = roomService.createRoom("Alice");
		VideoStateService videoStateService = new VideoStateService(roomService, fixedClock(4_000));
		videoStateService.ensureController(roomId, "Alice", "client-1");

		VideoStateMessage state = videoStateService.store(roomId, message(roomId, "pause", 10, false, 1_000))
			.orElseThrow();

		assertThat(state.currentTime()).isEqualTo(10);
		assertThat(state.updatedAt()).isEqualTo(1_000);
	}

	private VideoStateMessage message(String roomId, String type, double currentTime, boolean isPlaying, long updatedAt) {
		return new VideoStateMessage(roomId, "Alice", "client-1", type, "abc123", currentTime, isPlaying, updatedAt);
	}

	private Clock fixedClock(long millis) {
		return Clock.fixed(Instant.ofEpochMilli(millis), ZoneOffset.UTC);
	}
}
