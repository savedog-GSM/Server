package com.example.savedog.Telemetry.DTO;

import com.example.savedog.Telemetry.domain.SensorPosition;
import com.example.savedog.Telemetry.domain.SensorReading;
import com.example.savedog.Telemetry.domain.SensorType;

public record SensorReadingDto(
        SensorType type,
        SensorPosition position,
        double value
) {
    public static SensorReadingDto from(SensorReading reading) {
        return new SensorReadingDto(reading.getType(), reading.getPosition(), reading.getValue());
    }

    public SensorReading toEntity() {
        return new SensorReading(type, position, value);
    }
}
