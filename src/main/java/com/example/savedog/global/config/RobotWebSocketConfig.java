package com.example.savedog.global.config;

import com.example.savedog.Robot.websocket.RobotSocketHandler;
import com.example.savedog.global.security.RobotHandshakeInterceptor;
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
    private final RobotHandshakeInterceptor robotHandshakeInterceptor;

    // 허용 출처를 따로 열지 않는다. 로보독처럼 브라우저가 아닌 기기는 Origin 헤더가 없어 그대로 접속되고,
    // 다른 웹사이트를 보던 브라우저가 로보독인 척 접속하는 것은 막힌다.
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(robotSocketHandler, "/ws/robot")
                .addInterceptors(robotHandshakeInterceptor);
    }
}
