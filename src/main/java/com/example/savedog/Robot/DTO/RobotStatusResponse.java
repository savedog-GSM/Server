package com.example.savedog.Robot.DTO;

import com.example.savedog.Safety.DTO.RiskResponse;
import com.example.savedog.Telemetry.DTO.SensorValues;

import java.time.LocalDateTime;

// 대시보드 첫 화면용. 센서 값을 한 번도 받지 못했으면 connected 외의 값은 null
public record RobotStatusResponse(
        boolean connected,
        LocalDateTime lastSeenAt,
        LocalDateTime lastLocationAt,
        Double latitude,
        Double longitude,
        SensorValues sensor,
        RiskResponse risk
) {
}
