package com.echoroom.echoroom_backend.dto;

import java.util.List;

public record RoomResponse(String roomId, List<String> users, String message) {
}
