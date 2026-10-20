package com.example.savedog.Telemetry.DTO;

import com.example.savedog.Safety.DTO.RiskResponse;
import com.example.savedog.Telemetry.domain.Telemetry;

import java.time.LocalDateTime;

// 센서·위험도 스냅샷. 위치·속도는 최근 위치가 없으면 null
public record TelemetryResponse(
        Long id,
        Double latitude,
        Double longitude,
        Double speed,
        SensorValues sensor,
        RiskResponse risk,
        LocalDateTime recordedAt
) {
    public static TelemetryResponse from(Telemetry telemetry) {
        return new TelemetryResponse(
                telemetry.getId(),
                telemetry.getLatitude(),
                telemetry.getLongitude(),
                telemetry.getSpeed(),
                new SensorValues(telemetry.getDistance()),
                riskOf(telemetry),
                telemetry.getRecordedAt()
        );
    }

    // 위험도 판단이 붙기 전에 저장된 옛 기록은 risk가 null
    private static RiskResponse riskOf(Telemetry telemetry) {
        if (telemetry.getRiskLevel() == null || telemetry.getRiskScore() == null) {
            return null;
        }
        return new RiskResponse(telemetry.getRiskLevel(), telemetry.getRiskScore(), telemetry.getRiskReason());
    }
}
