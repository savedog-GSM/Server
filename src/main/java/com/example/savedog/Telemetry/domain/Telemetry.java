package com.example.savedog.Telemetry.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Telemetry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long robotId;

    private double latitude;

    private double longitude;

    private double speed;

    @ElementCollection
    @CollectionTable(name = "telemetry_sensor_reading", joinColumns = @JoinColumn(name = "telemetry_id"))
    private List<SensorReading> sensorReadings = new ArrayList<>();

    private LocalDateTime recordedAt;

    public Telemetry(
            Long robotId,
            double latitude,
            double longitude,
            double speed,
            List<SensorReading> sensorReadings
    ) {
        this.robotId = robotId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.sensorReadings.addAll(sensorReadings);
        this.recordedAt = LocalDateTime.now();
    }

}
