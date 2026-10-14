package com.example.savedog.Robot.websocket;

import com.example.savedog.Robot.service.RobotService;
import com.example.savedog.global.logging.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
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

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        sessionRegistry.markAlive(session);
        // 여기서 예외가 새면 스프링이 연결을 닫아 버리므로, 메시지 하나가 잘못됐다고 연결까지 끊기지 않게 막는다.
        try {
            robotService.handleAck(message.getPayload());
        } catch (RuntimeException e) {
            log.warn("로보독 메시지 처리 실패: {}", LogSanitizer.clean(message.getPayload()), e);
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        sessionRegistry.markAlive(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("로보독 연결 오류: {}", exception.toString());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(session);
    }
}
