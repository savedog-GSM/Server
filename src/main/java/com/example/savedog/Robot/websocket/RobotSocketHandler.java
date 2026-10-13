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
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

// ESP32가 ws://{서버}/ws/robot?robotId={id} 로 접속한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class RobotSocketHandler extends TextWebSocketHandler {
    private static final String ROBOT_ID_PARAM = "robotId";

    private final RobotSessionRegistry sessionRegistry;
    private final RobotService robotService;
    private final WebSocketService webSocketService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        Long robotId = resolveRobotId(session);
        if (robotId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        sessionRegistry.register(robotId, session);
        webSocketService.broadcastStatus(RobotConnectionEvent.of(robotId, true));
        log.info("로보독 {} 연결", robotId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long robotId = resolveRobotId(session);
        if (robotId != null) {
            robotService.handleAck(robotId, message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long robotId = resolveRobotId(session);
        if (robotId != null && sessionRegistry.unregister(robotId, session)) {
            webSocketService.broadcastStatus(RobotConnectionEvent.of(robotId, false));
            log.info("로보독 {} 연결 종료 ({})", robotId, status);
        }
    }

    private Long resolveRobotId(WebSocketSession session) {
        if (session.getUri() == null) {
            return null;
        }
        String value = UriComponentsBuilder.fromUri(session.getUri())
                .build()
                .getQueryParams()
                .getFirst(ROBOT_ID_PARAM);
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
