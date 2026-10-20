package com.example.savedog.Robot.DTO;

import java.time.LocalDateTime;
import java.util.Map;

// 중계 프로그램이 보낸 이벤트(QR 인식 등)를 그대로 싣고, 서버가 받은 시각을 붙여 화면에 전달한다.
public record RobotEventMessage(
        Map<String, Object> event,
        LocalDateTime receivedAt
) {
}
