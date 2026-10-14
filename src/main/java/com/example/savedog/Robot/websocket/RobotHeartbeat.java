package com.example.savedog.Robot.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 15초마다 로보독이 살아 있는지 확인한다. 45초 동안 아무 응답이 없으면 끊긴 것으로 본다.
@Component
@RequiredArgsConstructor
public class RobotHeartbeat {
    private static final long INTERVAL_MS = 15_000;
    private static final Duration MAX_SILENCE = Duration.ofSeconds(45);

    private final RobotSessionRegistry sessionRegistry;

    @Scheduled(fixedDelay = INTERVAL_MS, initialDelay = INTERVAL_MS)
    public void check() {
        sessionRegistry.checkHeartbeat(MAX_SILENCE);
    }
}
