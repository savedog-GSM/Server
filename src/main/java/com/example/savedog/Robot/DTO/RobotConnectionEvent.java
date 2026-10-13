package com.example.savedog.Robot.DTO;

import java.time.LocalDateTime;

public record RobotConnectionEvent(
        boolean connected,
        LocalDateTime occurredAt
) {
    public static RobotConnectionEvent of(boolean connected) {
        return new RobotConnectionEvent(connected, LocalDateTime.now());
    }
}
