package com.example.savedog.Telemetry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorReading {
    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type")
    private SensorType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_position")
    private SensorPosition position;

    @Column(name = "sensor_value")
    private double value;
}
