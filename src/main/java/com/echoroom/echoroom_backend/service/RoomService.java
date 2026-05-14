package com.echoroom.echoroom_backend.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

@Service
public class RoomService {

	private final ConcurrentMap<String, RoomData> rooms = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, String> sessionRoom = new ConcurrentHashMap<>();

	public String createRoom(String ownerName) {
		String roomId = generateRoomId();
		rooms.put(roomId, new RoomData());
		return roomId;
	}

	public boolean roomExists(String roomId) {
		return normalizeRoomId(roomId)
			.map(rooms::containsKey)
			.orElse(false);
	}

	public List<String> getRoomUsers(String roomId) {
		RoomData room = normalizeRoomId(roomId)
			.map(rooms::get)
			.orElse(null);
		if (room == null) {
			return Collections.emptyList();
		}
		return new ArrayList<>(room.users.values());
	}

	public List<String> addUserToRoom(String roomId, String sessionId, String name) {
		String normalizedRoomId = normalizeRoomId(roomId)
			.orElseThrow(() -> new IllegalArgumentException("roomId is required"));
		if (sessionId == null || sessionId.isBlank()) {
			throw new IllegalArgumentException("sessionId is required");
		}
		String displayName = normalizeName(name)
			.orElseThrow(() -> new IllegalArgumentException("name is required"));

		RoomData room = rooms.get(normalizedRoomId);
		if (room == null) {
			throw new IllegalStateException("Room not found: " + normalizedRoomId);
		}
		removeUser(sessionId);
		room.users.put(sessionId, displayName);
		sessionRoom.put(sessionId, normalizedRoomId);
		return getRoomUsers(normalizedRoomId);
	}

	public RoomUsersSnapshot removeUser(String sessionId) {
		String roomId = sessionRoom.remove(sessionId);
		if (roomId == null) {
			return RoomUsersSnapshot.empty();
		}
		RoomData room = rooms.get(roomId);
		if (room == null) {
			return RoomUsersSnapshot.empty();
		}
		room.users.remove(sessionId);
		return new RoomUsersSnapshot(roomId, getRoomUsers(roomId));
	}

	public Optional<String> normalizeRoomId(String roomId) {
		if (roomId == null || roomId.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(roomId.trim().toUpperCase());
	}

	public Optional<String> normalizeName(String name) {
		if (name == null || name.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(name.trim());
	}

	private String generateRoomId() {
		String candidate = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
		while (rooms.containsKey(candidate)) {
			candidate = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
		}
		return candidate;
	}

	public record RoomUsersSnapshot(String roomId, List<String> users) {

		private static RoomUsersSnapshot empty() {
			return new RoomUsersSnapshot(null, List.of());
		}
	}

	private static final class RoomData {
		private final Map<String, String> users = new ConcurrentHashMap<>();
	}
}
