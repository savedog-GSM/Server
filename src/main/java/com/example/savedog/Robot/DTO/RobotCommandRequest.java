package com.example.savedog.Robot.DTO;

import com.example.savedog.Robot.domain.CommandType;

// speed: 0~100(%), durationMs: null이면 다음 명령이 올 때까지 유지
public record RobotCommandRequest(
        CommandType type,
        Integer speed,
        Integer durationMs
) {
}
