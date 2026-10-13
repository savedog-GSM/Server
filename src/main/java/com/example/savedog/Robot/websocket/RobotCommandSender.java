package com.example.savedog.Robot.websocket;

import com.example.savedog.Robot.DTO.RobotCommandMessage;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RobotCommandSender {
    private final RobotSessionRegistry sessionRegistry;
    private final JsonMapper jsonMapper;

    public void send(RobotCommandMessage message) {
        WebSocketSession session = sessionRegistry.find()
                .orElseThrow(() -> new BusinessException(ErrorCode.ROBOT_NOT_CONNECTED));
        try {
            session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(message)));
        } catch (IOException e) {
            log.warn("로보독 명령 전송 실패", e);
            throw new BusinessException(ErrorCode.ROBOT_NOT_CONNECTED);
        }
    }
}
