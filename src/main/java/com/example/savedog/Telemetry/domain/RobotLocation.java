package com.example.savedog.Telemetry.domain;

import java.time.LocalDateTime;

// 스마트폰이 보낸 위치 한 건. accuracy(오차 반경 m)와 speed(m/s)는 기기가 못 구하면 null
public record RobotLocation(
        double latitude,
        double longitude,
        Double accuracy,
        Double speed,
        LocalDateTime receivedAt
) {
}
