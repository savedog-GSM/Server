package com.example.savedog.Telemetry.service;

import com.example.savedog.Telemetry.DTO.LocationRequest;
import com.example.savedog.Telemetry.domain.RobotLocation;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

// 스마트폰이 보낸 위치 중 가장 최근 것 하나만 기억한다. 기록은 센서 값이 올 때 만들어진다.
@Service
@RequiredArgsConstructor
public class LocationService {
    private static final Duration MAX_AGE = Duration.ofSeconds(5);
    private static final double MAX_ACCURACY_METERS = 30;

    private final Clock clock;
    private final AtomicReference<RobotLocation> latest = new AtomicReference<>();

    public void store(LocationRequest request) {
        RequestValidator.required(request, "요청 본문");
        GeoPoint point = RequestValidator.coordinate(request.latitude(), request.longitude(), "위치");
        // 오차가 아주 큰 위치도 받기는 한다. 쓸지 말지는 findRecent에서 거른다.
        if (request.accuracy() != null) {
            RequestValidator.nonNegative(request.accuracy(), "accuracy");
        }
        if (request.speed() != null) {
            RequestValidator.nonNegative(request.speed(), "speed");
        }
        latest.set(new RobotLocation(
                point.latitude(),
                point.longitude(),
                request.accuracy(),
                request.speed(),
                LocalDateTime.now(clock)
        ));
    }

    // 센서 값에 붙여도 되는 위치만 돌려준다: 받은 지 5초 이내이고 오차 반경이 30m 이하
    public Optional<RobotLocation> findRecent() {
        return findRecent(MAX_AGE);
    }

    // /api/robot/status에서 센서 수신 여부와 관계없이 마지막으로 받은 위치를 표시할 때 쓴다.
    public Optional<RobotLocation> latest() {
        return Optional.ofNullable(latest.get());
    }

    // 받은 지 maxAge 이내이고 오차 반경이 30m 이하인 위치. 로보독이 멈춰 선 자리의 신고처럼 조금 지난 위치도 쓸 때
    public Optional<RobotLocation> findRecent(Duration maxAge) {
        RobotLocation location = latest.get();
        if (location == null) {
            return Optional.empty();
        }
        boolean fresh = Duration.between(location.receivedAt(), LocalDateTime.now(clock)).compareTo(maxAge) <= 0;
        boolean accurate = location.accuracy() == null || location.accuracy() <= MAX_ACCURACY_METERS;
        return fresh && accurate ? Optional.of(location) : Optional.empty();
    }

    // 쓸 수 있는 위치인지와 상관없이, 마지막으로 위치를 받은 시각
    public Optional<LocalDateTime> lastReceivedAt() {
        return Optional.ofNullable(latest.get()).map(RobotLocation::receivedAt);
    }
}
