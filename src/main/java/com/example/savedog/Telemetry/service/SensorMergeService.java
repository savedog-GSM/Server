package com.example.savedog.Telemetry.service;

import com.example.savedog.Telemetry.DTO.SensorValues;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 거리는 아두이노가, 기울기는 로보독이 따로 보낸다.
 * 한쪽 값만 담긴 요청이 오면 다른 장치가 최근에 보낸 값을 붙여, 한 시점의 센서 값 한 묶음으로 만든다.
 */
@Service
@RequiredArgsConstructor
public class SensorMergeService {
    // 다른 장치의 값은 받은 지 3초 이내일 때만 붙인다. 각 장치가 1초마다 보내므로 두 번까지 빠져도 이어진다.
    private static final Duration MAX_AGE = Duration.ofSeconds(3);

    private final Clock clock;
    private final AtomicReference<Reading> lastDistance = new AtomicReference<>();
    private final AtomicReference<Reading> lastTilt = new AtomicReference<>();

    // 이번에 온 값은 기억해 두고, 빠진 값은 최근 값으로 채운다. 최근 값도 없으면 null로 둔다.
    public SensorValues merge(SensorValues received) {
        Instant now = clock.instant();
        if (received.hasDistance()) {
            lastDistance.set(new Reading(received, now));
        }
        if (received.hasTilt()) {
            lastTilt.set(new Reading(received, now));
        }
        SensorValues distanceSource = recent(lastDistance, now);
        SensorValues tiltSource = recent(lastTilt, now);
        return new SensorValues(
                distanceSource == null ? null : distanceSource.distance(),
                tiltSource == null ? null : tiltSource.roll(),
                tiltSource == null ? null : tiltSource.pitch()
        );
    }

    private SensorValues recent(AtomicReference<Reading> last, Instant now) {
        Reading reading = last.get();
        if (reading == null || Duration.between(reading.receivedAt(), now).compareTo(MAX_AGE) > 0) {
            return null;
        }
        return reading.values();
    }

    private record Reading(SensorValues values, Instant receivedAt) {
    }
}
