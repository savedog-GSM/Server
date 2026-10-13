package com.example.savedog.Telemetry.DTO;

import com.example.savedog.Safety.DTO.RiskResponse;
import com.example.savedog.Telemetry.domain.Telemetry;

import java.time.LocalDateTime;

// 센서·위험도 스냅샷. 위치·속도는 최근 위치가 없으면 null, risk는 위험도 판단 전이면 null
public record TelemetryResponse(
        Long id,
        Double latitude,
        Double longitude,
        Double speed,
        SensorValues sensor,
        RiskResponse risk,
        LocalDateTime recordedAt
) {
    public static TelemetryResponse of(Telemetry telemetry, RiskResponse risk) {
        return new TelemetryResponse(
                telemetry.getId(),
                telemetry.getLatitude(),
                telemetry.getLongitude(),
                telemetry.getSpeed(),
                new SensorValues(telemetry.getDistance(), telemetry.getRoll(), telemetry.getPitch()),
                risk,
                telemetry.getRecordedAt()
        );
    }
}
