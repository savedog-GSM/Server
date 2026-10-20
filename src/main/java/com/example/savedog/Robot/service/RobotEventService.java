package com.example.savedog.Robot.service;

import com.example.savedog.Robot.DTO.RobotEventMessage;
import com.example.savedog.global.logging.LogSanitizer;
import com.example.savedog.global.validation.RequestValidator;
import com.example.savedog.global.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 중계 프로그램이 알리는 이벤트(QR 인식 등)를 관제 화면에 실시간으로 전달한다. 저장하지는 않는다.
 * 이벤트의 항목은 중계 프로그램이 정하므로 JSON 객체를 그대로 넘긴다. (본문 크기는 64KB로 제한된다)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RobotEventService {
    private static final int MAX_FIELDS = 50;

    private final WebSocketService webSocketService;
    private final Clock clock;

    public void receive(Map<String, Object> event) {
        if (event == null || event.isEmpty()) {
            throw RequestValidator.invalid("이벤트 본문이 비어 있습니다. JSON 객체로 보내 주세요.");
        }
        if (event.size() > MAX_FIELDS) {
            throw RequestValidator.invalid("이벤트 항목은 %d개 이하여야 합니다.".formatted(MAX_FIELDS));
        }
        webSocketService.broadcastRobotEvent(new RobotEventMessage(event, LocalDateTime.now(clock)));
        log.info("로보독 이벤트: {}", LogSanitizer.clean(event.toString()));
    }
}
