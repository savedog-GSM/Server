package com.example.savedog.global.config;

import com.example.savedog.Robot.websocket.RobotSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// ESP32 전용 순수 WebSocket. 웹 클라이언트용 STOMP(/ws)는 WebSocketConfig 참고.
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RobotWebSocketConfig implements WebSocketConfigurer {
    private final RobotSocketHandler robotSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(robotSocketHandler, "/ws/robot")
                .setAllowedOriginPatterns("*");
    }
}
