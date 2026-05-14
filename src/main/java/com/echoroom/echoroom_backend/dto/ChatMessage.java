package com.echoroom.echoroom_backend.dto;

public record ChatMessage(String roomId, String sender, String content) {
}
