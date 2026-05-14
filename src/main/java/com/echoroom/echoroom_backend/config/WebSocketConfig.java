package com.echoroom.echoroom_backend.config;

import com.echoroom.echoroom_backend.socket.RoomWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final RoomWebSocketHandler roomWebSocketHandler;
	private final String[] allowedOriginPatterns;

	public WebSocketConfig(
		RoomWebSocketHandler roomWebSocketHandler,
		@Value("${app.cors.allowed-origin-patterns:*}") String[] allowedOriginPatterns) {
		this.roomWebSocketHandler = roomWebSocketHandler;
		this.allowedOriginPatterns = allowedOriginPatterns;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(roomWebSocketHandler, "/ws")
			.setAllowedOriginPatterns(allowedOriginPatterns);
	}
}
