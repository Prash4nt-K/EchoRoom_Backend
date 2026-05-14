package com.echoroom.echoroom_backend;

import static org.assertj.core.api.Assertions.assertThat;

import com.echoroom.echoroom_backend.service.RoomService;
import org.junit.jupiter.api.Test;

class RoomServiceTests {

	private final RoomService roomService = new RoomService();

	@Test
	void createRoomCreatesUppercaseEightCharacterRoomId() {
		String roomId = roomService.createRoom("Alice");

		assertThat(roomId).hasSize(8);
		assertThat(roomService.roomExists(roomId)).isTrue();
		assertThat(roomService.roomExists(roomId.toLowerCase())).isTrue();
	}

	@Test
	void addUserTrimsNameAndMovesSessionBetweenRooms() {
		String firstRoom = roomService.createRoom("Alice");
		String secondRoom = roomService.createRoom("Bob");

		roomService.addUserToRoom(firstRoom, "session-1", "  Alice  ");
		roomService.addUserToRoom(secondRoom, "session-1", " Alice ");

		assertThat(roomService.getRoomUsers(firstRoom)).isEmpty();
		assertThat(roomService.getRoomUsers(secondRoom)).containsExactly("Alice");
	}

	@Test
	void removeUserReturnsUpdatedUsersForRoom() {
		String roomId = roomService.createRoom("Alice");
		roomService.addUserToRoom(roomId, "session-1", "Alice");
		roomService.addUserToRoom(roomId, "session-2", "Bob");

		assertThat(roomService.removeUser("session-1").users()).containsExactly("Bob");
	}
}
