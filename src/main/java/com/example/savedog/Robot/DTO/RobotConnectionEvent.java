package com.example.savedog.Robot.DTO;

import java.time.LocalDateTime;

public record RobotConnectionEvent(
        Long robotId,
        boolean connected,
        LocalDateTime occurredAt
) {
    public static RobotConnectionEvent of(Long robotId, boolean connected) {
        return new RobotConnectionEvent(robotId, connected, LocalDateTime.now());
    }
}
