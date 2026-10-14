package com.example.savedog.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

// 테스트에서 시간을 원하는 만큼 흘려보내기 위한 시계
public class MutableClock extends Clock {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private Instant now = LocalDateTime.of(2026, 10, 13, 14, 0).atZone(ZONE).toInstant();

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void advanceSeconds(long seconds) {
        advance(Duration.ofSeconds(seconds));
    }

    @Override
    public ZoneId getZone() {
        return ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
