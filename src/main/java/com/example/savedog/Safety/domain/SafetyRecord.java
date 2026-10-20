package com.example.savedog.Safety.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 지도·경로 탐색은 위도·경도 범위로, 구간 조회는 최근 순서로 읽는다.
@Table(indexes = {
        @Index(name = "idx_safety_record_assessed_at", columnList = "assessed_at"),
        @Index(name = "idx_safety_record_location", columnList = "latitude, longitude")
})
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
