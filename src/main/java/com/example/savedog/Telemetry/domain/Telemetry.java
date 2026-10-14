package com.example.savedog.Telemetry.domain;

import com.example.savedog.Safety.domain.SafetyLevel;
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
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 위치·속도는 스마트폰이 보낸 값이라 최근 위치가 없으면 null
    private Double latitude;

    private Double longitude;

    private Double locationAccuracy;

    private Double speed;

    // 거리는 아두이노가, 기울기는 로보독이 따로 보낸다. 한쪽 장치가 조용하면 그쪽 값은 null
    private Double distance;

    private Double roll;

    private Double pitch;

    // 위험도 판단 결과. 위치가 없는 기록도 대시보드와 이동 기록에서 위험도를 볼 수 있게 여기에도 남긴다.
    @Enumerated(EnumType.STRING)
    private SafetyLevel riskLevel;

    private Integer riskScore;

    private String riskReason;

    private LocalDateTime recordedAt;

    public Telemetry(
            RobotLocation location,
            Double distance,
            Double roll,
            Double pitch
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

    public void applyRisk(SafetyLevel level, int score, String reason) {
        this.riskLevel = level;
        this.riskScore = score;
        this.riskReason = reason;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
