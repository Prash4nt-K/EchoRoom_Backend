package com.echoroom.echoroom_backend.controller;

import java.util.List;

import com.echoroom.echoroom_backend.dto.JoinRoomRequest;
import com.echoroom.echoroom_backend.dto.RoomCreateRequest;
import com.echoroom.echoroom_backend.dto.RoomResponse;
import com.echoroom.echoroom_backend.service.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

	private final RoomService roomService;

	public RoomController(RoomService roomService) {
		this.roomService = roomService;
	}

	@PostMapping("/create")
	public ResponseEntity<RoomResponse> createRoom(@RequestBody RoomCreateRequest request) {
		if (request == null || roomService.normalizeName(request.name()).isEmpty()) {
			return ResponseEntity.badRequest()
				.body(new RoomResponse(null, List.of(), "name is required"));
		}

		String roomId = roomService.createRoom(request.name());
		return ResponseEntity.ok(new RoomResponse(roomId, roomService.getRoomUsers(roomId), "Room created"));
	}

	@PostMapping("/join")
	public ResponseEntity<RoomResponse> joinRoom(@RequestBody JoinRoomRequest request) {
		if (request == null || roomService.normalizeName(request.name()).isEmpty()) {
			return ResponseEntity.badRequest()
				.body(new RoomResponse(null, List.of(), "name is required"));
		}

		return roomService.normalizeRoomId(request.roomId())
			.map(this::joinExistingRoom)
			.orElseGet(() -> ResponseEntity.badRequest()
				.body(new RoomResponse(null, List.of(), "roomId is required")));
	}

	private ResponseEntity<RoomResponse> joinExistingRoom(String roomId) {
		if (!roomService.roomExists(roomId)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(new RoomResponse(roomId, List.of(), "Room not found"));
		}
		return ResponseEntity.ok(new RoomResponse(roomId, roomService.getRoomUsers(roomId), "Room available"));
	}
}
