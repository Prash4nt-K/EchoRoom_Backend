package com.echoroom.echoroom_backend.dto;

import java.util.List;

public record RoomUsersUpdate(List<String> users, int count) {
}
