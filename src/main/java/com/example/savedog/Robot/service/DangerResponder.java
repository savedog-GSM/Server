package com.example.savedog.Robot.service;

import com.example.savedog.Robot.DTO.DangerAlert;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
import com.example.savedog.global.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 위험 등급(위험도 5)으로 바뀌는 순간 한 번, 로보독을 멈춰 앉히고 짖게 하고(기능명세서 F-12)
 * 관제 화면에 알림을 보낸다(F-16). 위험이 이어지는 동안에는 다시 보내지 않는다.
 * 다만 로보독이 끊겨 있어 멈춤 신호를 보내지 못했다면 위험이 계속되는 한 다음 센서 값에서 다시 시도한다.
 * 알림은 다시 보내지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DangerResponder {
    private final RobotService robotService;
    private final WebSocketService webSocketService;

    private boolean inDanger;
    private boolean stopDelivered;

    public synchronized void onAssessed(TelemetryResponse snapshot) {
        if (snapshot.risk() == null || snapshot.risk().level() != SafetyLevel.DANGER) {
            inDanger = false;
            stopDelivered = false;
            return;
        }
        boolean justBecameDanger = !inDanger;
        inDanger = true;

        if (!stopDelivered) {
            stopDelivered = tryStop();
        }
        if (justBecameDanger) {
            webSocketService.broadcastAlert(DangerAlert.of(snapshot, stopDelivered));
        }
    }

    // 멈춤 신호를 보내다 예상하지 못한 오류가 나도 화면 알림까지 막히지 않게 한다.
    private boolean tryStop() {
        try {
            return robotService.emergencyStop();
        } catch (RuntimeException e) {
            log.error("위험 감지: 로보독을 멈추는 중 예상하지 못한 오류", e);
            return false;
        }
    }
}
