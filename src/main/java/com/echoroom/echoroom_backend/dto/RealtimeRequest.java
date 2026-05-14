package com.echoroom.echoroom_backend.dto;

import tools.jackson.databind.JsonNode;

public record RealtimeRequest(String event, JsonNode data) {
}
