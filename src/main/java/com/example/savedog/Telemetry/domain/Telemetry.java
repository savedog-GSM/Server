package com.example.savedog.Telemetry.domain;

import com.example.savedog.Safety.domain.SafetyLevel;
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
// 최근 기록·기간 조회가 모두 recorded_at 순서로 읽는다.
@Table(indexes = @Index(name = "idx_telemetry_recorded_at", columnList = "recorded_at"))
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 위치·속도는 스마트폰이 보낸 값이라 최근 위치가 없으면 null
    private Double latitude;

    private Double longitude;

    private Double locationAccuracy;

    private Double speed;

    // 아두이노가 잰 전방 거리(cm). 새 기록에는 항상 있다.
    // 거리 없이 기울기만 받던 예전 버전의 기록을 읽을 수 있게 null을 허용하는 타입으로 둔다.
    private Double distance;

    // 위험도 판단 결과. 위치가 없는 기록도 대시보드와 이동 기록에서 위험도를 볼 수 있게 여기에도 남긴다.
    @Enumerated(EnumType.STRING)
    private SafetyLevel riskLevel;

    private Integer riskScore;

    private String riskReason;

    private LocalDateTime recordedAt;

    public Telemetry(
            RobotLocation location,
            double distance
    ) {
        if (location != null) {
            this.latitude = location.latitude();
            this.longitude = location.longitude();
            this.locationAccuracy = location.accuracy();
            this.speed = location.speed();
        }
        this.distance = distance;
        this.recordedAt = LocalDateTime.now();
    }

    public void applyRisk(SafetyLevel level, int score, String reason) {
        this.riskLevel = level;
        this.riskScore = score;
        this.riskReason = reason;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
