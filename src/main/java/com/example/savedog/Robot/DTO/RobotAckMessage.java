package com.example.savedog.Robot.DTO;

// ESP32 → 서버 WebSocket 메시지. result: "OK" 또는 실패 사유
public record RobotAckMessage(
        long commandId,
        String result
) {
}
