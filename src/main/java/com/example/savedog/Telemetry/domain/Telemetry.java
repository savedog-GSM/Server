package com.example.savedog.Telemetry.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 위치·속도는 스마트폰이 보낸 값이라 최근 위치가 없으면 null
    private Double latitude;

    private Double longitude;

    private Double locationAccuracy;

    private Double speed;

    private double distance;

    private double roll;

    private double pitch;

    private LocalDateTime recordedAt;

    public Telemetry(
            RobotLocation location,
            double distance,
            double roll,
            double pitch
    ) {
        if (location != null) {
            this.latitude = location.latitude();
            this.longitude = location.longitude();
            this.locationAccuracy = location.accuracy();
            this.speed = location.speed();
        }
        this.distance = distance;
        this.roll = roll;
        this.pitch = pitch;
        this.recordedAt = LocalDateTime.now();
    }
}
