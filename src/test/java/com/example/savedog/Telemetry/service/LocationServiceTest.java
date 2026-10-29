package com.example.savedog.Telemetry.service;

import com.example.savedog.Telemetry.DTO.LocationRequest;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationServiceTest {
    private final MutableClock clock = new MutableClock();
    private final LocationService service = new LocationService(clock);

    @Test
    @DisplayName("방금 받은 위치는 센서 값에 붙일 수 있다")
    void freshLocationIsUsable() {
        service.store(new LocationRequest(37.5665, 126.978, 8.0));

        var location = service.findRecent().orElseThrow();
        assertEquals(37.5665, location.latitude());
        assertEquals(126.978, location.longitude());
    }

    @Test
    @DisplayName("받은 지 30초가 넘은 위치는 붙이지 않는다 (제자리에서 위치가 뜸하게 와도 30초까지는 붙인다)")
    void staleLocationIsIgnored() {
        service.store(new LocationRequest(37.5665, 126.978, 8.0));

        clock.advanceSeconds(30);
        assertTrue(service.findRecent().isPresent());

        clock.advanceSeconds(1);
        assertTrue(service.findRecent().isEmpty());
        assertTrue(service.lastReceivedAt().isPresent());
    }

    @Test
    @DisplayName("오차 반경이 30m보다 큰 위치는 받기는 하지만 쓰지 않는다")
    void inaccurateLocationIsIgnored() {
        service.store(new LocationRequest(37.5665, 126.978, 30.0));
        assertTrue(service.findRecent().isPresent());

        service.store(new LocationRequest(37.5665, 126.978, 30.1));
        assertTrue(service.findRecent().isEmpty());
    }

    @Test
    @DisplayName("오차 반경을 모르는 위치는 쓴다")
    void unknownAccuracyIsUsable() {
        service.store(new LocationRequest(37.5665, 126.978, null));

        assertTrue(service.findRecent().isPresent());
    }

    @Test
    @DisplayName("위치를 받은 적이 없으면 비어 있다")
    void nothingBeforeFirstLocation() {
        assertTrue(service.findRecent().isEmpty());
        assertTrue(service.lastReceivedAt().isEmpty());
    }

    @Test
    @DisplayName("빠졌거나 범위를 벗어난 값은 거절한다")
    void rejectsInvalidValues() {
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(null));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(new LocationRequest(null, 126.978, null)));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(new LocationRequest(37.5, null, null)));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(new LocationRequest(90.1, 126.978, null)));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(new LocationRequest(37.5, -180.1, null)));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(new LocationRequest(37.5, 126.978, -1.0)));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(new LocationRequest(Double.NaN, 126.978, null)));
        assertTrue(service.findRecent().isEmpty());
    }
}
