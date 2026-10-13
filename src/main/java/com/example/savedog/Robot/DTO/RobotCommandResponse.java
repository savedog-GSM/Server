package com.example.savedog.Robot.DTO;

import com.example.savedog.Robot.domain.CommandType;

import java.time.LocalDateTime;

public record RobotCommandResponse(
        long commandId,
        CommandType type,
        LocalDateTime sentAt
) {
}
