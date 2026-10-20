package com.example.savedog.Robot.DTO;

import com.example.savedog.Robot.domain.CommandType;

// speed: 0~100(%), durationMs: null이면 다음 명령이 올 때까지 유지
// degrees: 회전(TURN_LEFT, TURN_RIGHT)에서만 쓴다. 주면 그 각도만큼 돌고 멈춘다.
public record RobotCommandRequest(
        CommandType type,
        Integer speed,
        Integer durationMs,
        Integer degrees
) {
    public RobotCommandRequest(CommandType type, Integer speed, Integer durationMs) {
        this(type, speed, durationMs, null);
    }
}
