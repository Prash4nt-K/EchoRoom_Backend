package com.echoroom.echoroom_backend.dto;

public record JoinRoomRequest(String roomId, String name, String senderClientId) {
}
