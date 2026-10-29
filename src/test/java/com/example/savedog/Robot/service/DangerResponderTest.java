package com.example.savedog.Robot.service;

import com.example.savedog.Robot.DTO.DangerAlert;
import com.example.savedog.Safety.DTO.RiskResponse;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Telemetry.DTO.SensorValues;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
import com.example.savedog.global.websocket.WebSocketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DangerResponderTest {
    private final RobotService robotService = mock(RobotService.class);
    private final WebSocketService webSocketService = mock(WebSocketService.class);
    private final DangerResponder responder = new DangerResponder(robotService, webSocketService);

    @Test
    @DisplayName("위험으로 바뀌는 순간 한 번만 대응하고, 이어지는 동안에는 다시 보내지 않는다")
    void respondsOncePerDanger() {
        when(robotService.emergencyStop()).thenReturn(true);

        responder.onAssessed(safe());
        responder.onAssessed(danger());
        responder.onAssessed(danger());
        responder.onAssessed(danger());

        verify(robotService, times(1)).emergencyStop();
        verify(webSocketService, times(1)).broadcastAlert(any());
    }

    @Test
    @DisplayName("위험이 풀렸다가 다시 위험이 되면 또 대응하고 또 알린다")
    void respondsAgainAfterRecovery() {
        when(robotService.emergencyStop()).thenReturn(true);

        responder.onAssessed(danger());
        responder.onAssessed(caution());
        responder.onAssessed(danger());

        verify(robotService, times(2)).emergencyStop();
        verify(webSocketService, times(2)).broadcastAlert(any());
    }

    @Test
    @DisplayName("로보독이 끊겨 있어 보내지 못했으면 멈춤 신호는 다시 시도하지만 알림은 한 번만 보낸다")
    void retriesStopButAlertsOnce() {
        when(robotService.emergencyStop()).thenReturn(false, false, true);

        responder.onAssessed(danger());
        responder.onAssessed(danger());
        responder.onAssessed(danger());
        responder.onAssessed(danger());

        verify(robotService, times(3)).emergencyStop();
        verify(webSocketService, times(1)).broadcastAlert(any());
    }

    @Test
    @DisplayName("안전이나 주의에서는 대응하지도 알리지도 않는다")
    void doesNothingBelowDanger() {
        responder.onAssessed(safe());
        responder.onAssessed(caution());

        verify(robotService, never()).emergencyStop();
        verify(webSocketService, never()).broadcastAlert(any());
    }

    @Test
    @DisplayName("알림에는 위험 사유와 로보독을 멈췄는지, 그때의 센서 값이 담긴다")
    void alertDescribesTheDanger() {
        when(robotService.emergencyStop()).thenReturn(true);
        TelemetryResponse snapshot = danger();

        responder.onAssessed(snapshot);

        DangerAlert alert = sentAlert();
        assertEquals("위험 감지: 전방 15cm 장애물. 로보독을 멈춰 앉히고 짖게 했습니다.", alert.message());
        assertTrue(alert.robotStopped());
        assertSame(snapshot, alert.telemetry());
    }

    @Test
    @DisplayName("로보독을 멈추지 못했으면 알림에 그 사실을 담는다")
    void alertTellsWhenRobotWasNotStopped() {
        when(robotService.emergencyStop()).thenReturn(false);

        responder.onAssessed(danger());

        DangerAlert alert = sentAlert();
        assertEquals("위험 감지: 전방 15cm 장애물. 로보독에 멈춤 신호를 보내지 못했습니다.", alert.message());
        assertFalse(alert.robotStopped());
    }

    @Test
    @DisplayName("멈춤 신호를 보내다 예상하지 못한 오류가 나도 화면 알림은 보낸다")
    void alertsEvenWhenStopFailsUnexpectedly() {
        when(robotService.emergencyStop()).thenThrow(new IllegalStateException("전송 오류"));

        responder.onAssessed(danger());

        assertFalse(sentAlert().robotStopped());
    }

    private DangerAlert sentAlert() {
        ArgumentCaptor<DangerAlert> alert = ArgumentCaptor.forClass(DangerAlert.class);
        verify(webSocketService).broadcastAlert(alert.capture());
        return alert.getValue();
    }

    private TelemetryResponse danger() {
        return snapshot(15, new RiskResponse(SafetyLevel.DANGER, 5, "전방 15cm 장애물"));
    }

    private TelemetryResponse caution() {
        return snapshot(40, new RiskResponse(SafetyLevel.CAUTION, 3, "전방 40cm 장애물"));
    }

    private TelemetryResponse safe() {
        return snapshot(150, new RiskResponse(SafetyLevel.SAFE, 1, null));
    }

    private TelemetryResponse snapshot(double distance, RiskResponse risk) {
        return new TelemetryResponse(
                1L, 37.5665, 126.9780, new SensorValues(distance), risk,
                LocalDateTime.of(2026, 10, 13, 14, 0)
        );
    }
}
