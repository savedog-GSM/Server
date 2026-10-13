package com.example.savedog.Safety.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
public class SafetyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long robotId;

    private Long telemetryId;

    private double latitude;

    private double longitude;

    @Enumerated(EnumType.STRING)
    private SafetyLevel level;

    // 0~100, 높을수록 안전
    private int score;

    private String reason;

    private LocalDateTime assessedAt;

    public SafetyRecord(
            Long robotId,
            Long telemetryId,
            double latitude,
            double longitude,
            SafetyLevel level,
            int score,
            String reason
    ) {
        this.robotId = robotId;
        this.telemetryId = telemetryId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.level = level;
        this.score = score;
        this.reason = reason;
        this.assessedAt = LocalDateTime.now();
    }

}
