package com.example.savedog.Telemetry.DTO;

import com.example.savedog.Telemetry.domain.SensorReading;
import com.example.savedog.Telemetry.domain.Telemetry;

import java.util.List;

public record TelemetryRequest(
        double latitude,
        double longitude,
        double speed,
        List<SensorReadingDto> sensors
) {
    public Telemetry toEntity(Long robotId) {
        List<SensorReading> readings = sensors == null
                ? List.of()
                : sensors.stream().map(SensorReadingDto::toEntity).toList();
        return new Telemetry(robotId, latitude, longitude, speed, readings);
    }
}
