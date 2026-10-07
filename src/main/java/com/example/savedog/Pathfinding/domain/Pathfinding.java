package com.example.savedog.Pathfinding.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pathfinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    private Long robotId;

    @Getter
    private double latitude;

    @Getter
    private double longitude;

    @Getter
    private double speed;

    @Getter
    private LocalDateTime recordedAt;

    public Pathfinding(
            Long robotId,
            double latitude,
            double longitude,
            double speed
    ) {
        this.robotId = robotId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.recordedAt = LocalDateTime.now();
    }

}
