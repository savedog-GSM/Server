package com.example.savedog.Robot.websocket;

import com.example.savedog.Robot.DTO.RobotCommandMessage;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
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
        String payload = jsonMapper.writeValueAsString(message);
        try {
            session.sendMessage(new TextMessage(payload));
        } catch (IOException | RuntimeException e) {
            // 전송 도중 끊겼거나(IOException), 밀린 메시지가 한도를 넘는 등 더 쓸 수 없는 연결이다.
            // 남겨 두면 "연결됨"으로 보이면서 명령은 계속 사라지므로 정리한다. 로보독은 다시 접속하면 된다.
            log.warn("로보독 명령 전송 실패. 연결을 정리합니다: {}", e.toString());
            sessionRegistry.discard(session, CloseStatus.SESSION_NOT_RELIABLE);
            throw new BusinessException(
                    ErrorCode.ROBOT_NOT_CONNECTED,
                    "로보독과의 연결이 끊겨 명령을 보내지 못했습니다.",
                    e
            );
        }
    }
}
