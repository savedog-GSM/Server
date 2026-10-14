package com.example.savedog.Telemetry.service;

import com.example.savedog.Telemetry.DTO.SensorValues;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SensorMergeServiceTest {
    private final MutableClock clock = new MutableClock();
    private final SensorMergeService service = new SensorMergeService(clock);

    @Test
    @DisplayName("아두이노가 보낸 거리에 로보독이 방금 보낸 기울기를 붙인다")
    void fillsTiltFromTheOtherDevice() {
        service.merge(tilt(-2.5, 4.1));
        clock.advanceSeconds(1);

        assertEquals(new SensorValues(35.2, -2.5, 4.1), service.merge(distance(35.2)));
    }

    @Test
    @DisplayName("로보독이 보낸 기울기에 아두이노가 방금 보낸 거리를 붙인다")
    void fillsDistanceFromTheOtherDevice() {
        service.merge(distance(35.2));
        clock.advanceSeconds(1);

        assertEquals(new SensorValues(35.2, -2.5, 4.1), service.merge(tilt(-2.5, 4.1)));
    }

    @Test
    @DisplayName("다른 장치가 아직 아무것도 보내지 않았으면 그쪽 값은 비워 둔다")
    void leavesMissingWhenNothingReceivedYet() {
        assertEquals(new SensorValues(35.2, null, null), service.merge(distance(35.2)));
    }

    @Test
    @DisplayName("다른 장치의 값은 3초까지만 붙이고, 그보다 오래되면 비워 둔다")
    void dropsStaleValues() {
        service.merge(tilt(-2.5, 4.1));

        clock.advanceSeconds(3);
        assertEquals(new SensorValues(10.0, -2.5, 4.1), service.merge(distance(10.0)));

        clock.advanceSeconds(1);
        assertEquals(new SensorValues(10.0, null, null), service.merge(distance(10.0)));
    }

    @Test
    @DisplayName("같은 종류의 값이 다시 오면 새 값으로 바꾼다")
    void newerValueReplacesOlderOne() {
        service.merge(distance(100.0));
        service.merge(distance(15.0));

        assertEquals(new SensorValues(15.0, 0.0, 0.0), service.merge(tilt(0.0, 0.0)));
    }

    @Test
    @DisplayName("한 장치가 세 값을 모두 보내면 받은 그대로 쓴다")
    void usesCompleteReadingAsIs() {
        service.merge(distance(100.0));
        service.merge(tilt(20.0, 20.0));

        assertEquals(new SensorValues(35.2, -2.5, 4.1), service.merge(new SensorValues(35.2, -2.5, 4.1)));
    }

    private SensorValues distance(double centimeters) {
        return new SensorValues(centimeters, null, null);
    }

    private SensorValues tilt(double roll, double pitch) {
        return new SensorValues(null, roll, pitch);
    }
}
