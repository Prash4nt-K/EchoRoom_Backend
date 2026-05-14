package com.echoroom.echoroom_backend.dto;

public record VideoStateMessage(
	String roomId,
	String sender,
	String senderClientId,
	String type,
	String videoId,
	double currentTime,
	boolean isPlaying,
	long updatedAt) {
}
