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

    private Long telemetryId;

    private double latitude;

    private double longitude;

    @Enumerated(EnumType.STRING)
    private SafetyLevel riskLevel;

    // 1~5, 높을수록 위험
    private int riskScore;

    private String reason;

    private LocalDateTime assessedAt;

    public SafetyRecord(
            Long telemetryId,
            double latitude,
            double longitude,
            SafetyLevel riskLevel,
            int riskScore,
            String reason
    ) {
        this.telemetryId = telemetryId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.reason = reason;
        this.assessedAt = LocalDateTime.now();
    }

}
