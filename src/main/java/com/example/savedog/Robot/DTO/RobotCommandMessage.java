package com.example.savedog.Robot.DTO;

import com.example.savedog.Robot.domain.CommandType;

// 서버 → ESP32 WebSocket 메시지
public record RobotCommandMessage(
        long commandId,
        CommandType type,
        Integer speed,
        Integer durationMs
) {
}
