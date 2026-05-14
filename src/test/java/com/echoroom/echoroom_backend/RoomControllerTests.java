package com.echoroom.echoroom_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.echoroom.echoroom_backend.controller.RoomController;
import com.echoroom.echoroom_backend.dto.JoinRoomRequest;
import com.echoroom.echoroom_backend.dto.RoomCreateRequest;
import com.echoroom.echoroom_backend.dto.RoomResponse;
import com.echoroom.echoroom_backend.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class RoomControllerTests {

	private final RoomService roomService = new RoomService();
	private final RoomController controller = new RoomController(roomService);

	@Test
	void createRoomReturnsRoomIdForValidName() {
		ResponseEntity<RoomResponse> response = controller.createRoom(new RoomCreateRequest("Alice"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().roomId()).matches("[A-Z0-9]{8}");
		assertThat(response.getBody().users()).isEmpty();
		assertThat(response.getBody().message()).isEqualTo("Room created");
	}

	@Test
	void createRoomRejectsBlankName() {
		ResponseEntity<RoomResponse> response = controller.createRoom(new RoomCreateRequest("  "));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo("name is required");
	}

	@Test
	void joinRoomRejectsMissingRoomId() {
		ResponseEntity<RoomResponse> response = controller.joinRoom(new JoinRoomRequest(null, "Alice", "client-1"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo("roomId is required");
	}

	@Test
	void joinRoomReturnsNotFoundForUnknownRoom() {
		ResponseEntity<RoomResponse> response = controller.joinRoom(new JoinRoomRequest("unknown", "Alice", "client-1"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().roomId()).isEqualTo("UNKNOWN");
		assertThat(response.getBody().message()).isEqualTo("Room not found");
	}
}
