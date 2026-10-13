package com.example.savedog.Telemetry.DTO;

import com.example.savedog.Telemetry.domain.Telemetry;

import java.time.LocalDateTime;
import java.util.List;

public record TelemetryResponse(
        Long id,
        Long robotId,
        double latitude,
        double longitude,
        double speed,
        List<SensorReadingDto> sensors,
        LocalDateTime recordedAt
) {
    public static TelemetryResponse from(Telemetry telemetry) {
        return new TelemetryResponse(
                telemetry.getId(),
                telemetry.getRobotId(),
                telemetry.getLatitude(),
                telemetry.getLongitude(),
                telemetry.getSpeed(),
                telemetry.getSensorReadings().stream().map(SensorReadingDto::from).toList(),
                telemetry.getRecordedAt()
        );
    }
}
