package com.example.savedog.Robot.websocket;

import com.example.savedog.Robot.DTO.RobotConnectionEvent;
import com.example.savedog.Robot.service.RobotService;
import com.example.savedog.global.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

// 로보독이 ws://{서버}/ws/robot 으로 접속한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class RobotSocketHandler extends TextWebSocketHandler {
    private final RobotSessionRegistry sessionRegistry;
    private final RobotService robotService;
    private final WebSocketService webSocketService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);
        webSocketService.broadcastStatus(RobotConnectionEvent.of(true));
        log.info("로보독 연결");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        robotService.handleAck(message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (sessionRegistry.unregister(session)) {
            webSocketService.broadcastStatus(RobotConnectionEvent.of(false));
            log.info("로보독 연결 종료 ({})", status);
        }
    }
}
