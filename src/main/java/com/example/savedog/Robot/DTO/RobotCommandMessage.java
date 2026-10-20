package com.example.savedog.Robot.DTO;

import com.example.savedog.Robot.domain.CommandType;
import com.fasterxml.jackson.annotation.JsonInclude;

// 서버 → 중계 프로그램 WebSocket 메시지
// degrees는 각도 회전일 때만 싣는다. 모르는 항목을 싫어하는 중계 프로그램도 그대로 받을 수 있게 비어 있으면 뺀다.
public record RobotCommandMessage(
        long commandId,
        CommandType type,
        Integer speed,
        Integer durationMs,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer degrees
) {
    public RobotCommandMessage(long commandId, CommandType type, Integer speed, Integer durationMs) {
        this(commandId, type, speed, durationMs, null);
    }
}
