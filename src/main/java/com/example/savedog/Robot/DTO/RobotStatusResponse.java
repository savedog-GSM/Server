package com.example.savedog.Robot.DTO;

import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.global.common.GeoPoint;

import java.time.LocalDateTime;

// 텔레메트리/안전도 기록이 아직 없으면 last* 값은 null
public record RobotStatusResponse(
        boolean connected,
        LocalDateTime lastTelemetryAt,
        GeoPoint lastLocation,
        SafetyLevel lastSafetyLevel
) {
}
