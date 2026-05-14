package com.echoroom.echoroom_backend.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class SocketIOConfig {

	@Bean
	public SocketIOServer socketIOServer(
		@Value("${socketio.host:0.0.0.0}") String host,
		@Value("${socketio.port:9092}") int port,
		@Value("${socketio.origin:*}") String origin) {
		Configuration config = new Configuration();
		config.setHostname(host);
		config.setPort(port);
		config.setOrigin(origin);
		return new SocketIOServer(config);
	}
}
