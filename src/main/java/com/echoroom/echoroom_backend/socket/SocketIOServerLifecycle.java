package com.echoroom.echoroom_backend.socket;

import com.corundumstudio.socketio.SocketIOServer;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
public class SocketIOServerLifecycle implements SmartLifecycle {

	private final SocketIOServer server;
	private boolean running;

	public SocketIOServerLifecycle(SocketIOServer server) {
		this.server = server;
	}

	@Override
	public void start() {
		server.start();
		running = true;
	}

	@Override
	public void stop() {
		server.stop();
		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}
}
