package com.example.savedog.Report.service;

import com.example.savedog.Safety.analyzer.SafetyAssessment;
import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.Telemetry.domain.RobotLocation;
import com.example.savedog.Telemetry.domain.Telemetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class HazardReportTriggerTest {
    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.9780;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;

    private final ReportService reportService = mock(ReportService.class);
    // 테스트에서는 신고 만들기를 같은 스레드에서 바로 돌린다.
    private final HazardReportTrigger trigger = new HazardReportTrigger(reportService, Runnable::run);
    private LocalDateTime clock = LocalDateTime.of(2026, 10, 13, 14, 0);

    @Test
    @DisplayName("같은 곳에서 위험이 5초 이상 이어지면 신고를 한 번 만든다")
    void createsReportAfterFiveSecondsOfDanger() {
        for (int second = 0; second < 5; second++) {
            danger(0);
        }
        verify(reportService, never()).createIfNew(any(), any(), any());

        danger(0);
        danger(0);
        danger(0);

        verify(reportService, times(1)).createIfNew(any(), any(), any());
    }

    @Test
    @DisplayName("순간적으로 튄 위험 값으로는 신고를 만들지 않는다")
    void shortSpikeDoesNotCreateReport() {
        danger(0);
        danger(0);
        safe(0);
        danger(0);
        danger(0);
        safe(0);

        verify(reportService, never()).createIfNew(any(), any(), any());
    }

    @Test
    @DisplayName("15m 넘게 이동하면 다른 곳으로 보고 다시 5초를 센다")
    void movingAwayRestartsTheCount() {
        danger(0);
        danger(0);
        danger(0);
        danger(20);
        danger(20);
        danger(20);
        verify(reportService, never()).createIfNew(any(), any(), any());

        danger(20);
        danger(20);
        danger(20);

        verify(reportService, times(1)).createIfNew(any(), any(), any());
    }

    @Test
    @DisplayName("위치가 없는 위험 기록으로는 신고를 만들지 않는다")
    void noLocationNoReport() {
        for (int second = 0; second < 10; second++) {
            dangerWithoutLocation();
        }

        verify(reportService, never()).createIfNew(any(), any(), any());
    }

    @Test
    @DisplayName("센서 값이 10초 넘게 끊겼다 다시 오면 이어진 것으로 보지 않는다")
    void longSilenceRestartsTheCount() {
        danger(0);
        danger(0);
        clock = clock.plusSeconds(30);
        danger(0);
        danger(0);

        verify(reportService, never()).createIfNew(any(), any(), any());
    }

    @Test
    @DisplayName("위험이 풀렸다가 다시 5초 이상 이어지면 또 만든다")
    void newDangerAfterRecoveryCreatesAgain() {
        for (int second = 0; second < 6; second++) {
            danger(0);
        }
        safe(0);
        for (int second = 0; second < 6; second++) {
            danger(0);
        }

        verify(reportService, times(2)).createIfNew(any(), any(), any());
    }

    @Test
    @DisplayName("신고 만들기에 실패해도 같은 위험이 이어지는 동안 매초 다시 시도하지 않는다")
    void doesNotRetryEverySecondAfterFailure() {
        doThrow(new IllegalStateException("DB 오류")).when(reportService).createIfNew(any(), any(), any());

        // 실패는 로그로만 남고 센서 값 처리 쪽으로 번지지 않는다.
        for (int second = 0; second < 10; second++) {
            danger(0);
        }

        verify(reportService, times(1)).createIfNew(any(), any(), any());
    }

    @Test
    @DisplayName("신고 만들기는 넘겨받은 실행기에서 돌고, 센서 값을 처리하는 쪽은 기다리지 않는다")
    void createsReportOnTheGivenExecutor() {
        List<Runnable> queued = new ArrayList<>();
        HazardReportTrigger queuing = new HazardReportTrigger(reportService, queued::add);

        for (int second = 0; second < 8; second++) {
            feed(queuing, 0, new SafetyAssessment(SafetyLevel.DANGER, 5, "전방 15cm 장애물", HazardType.OBSTACLE), true);
        }

        assertEquals(1, queued.size());
        verify(reportService, never()).createIfNew(any(), any(), any());

        queued.get(0).run();
        verify(reportService, times(1)).createIfNew(any(), any(), any());
    }

    // 1초에 한 번씩 센서 값이 들어온 것처럼 만든다. northMeters는 기준점에서 북쪽으로 떨어진 거리
    private void danger(double northMeters) {
        feed(northMeters, new SafetyAssessment(SafetyLevel.DANGER, 5, "전방 15cm 장애물", HazardType.OBSTACLE), true);
    }

    private void safe(double northMeters) {
        feed(northMeters, new SafetyAssessment(SafetyLevel.SAFE, 1, null, null), true);
    }

    private void dangerWithoutLocation() {
        feed(0, new SafetyAssessment(SafetyLevel.DANGER, 5, "전방 15cm 장애물", HazardType.OBSTACLE), false);
    }

    private void feed(double northMeters, SafetyAssessment assessment, boolean located) {
        feed(trigger, northMeters, assessment, located);
    }

    private void feed(HazardReportTrigger target, double northMeters, SafetyAssessment assessment, boolean located) {
        double latitude = LATITUDE + northMeters / METERS_PER_LATITUDE_DEGREE;
        RobotLocation location = located ? new RobotLocation(latitude, LONGITUDE, 5.0, 0.0, clock) : null;
        Telemetry telemetry = new Telemetry(location, 15);
        ReflectionTestUtils.setField(telemetry, "recordedAt", clock);
        SafetyRecord record = located
                ? new SafetyRecord(1L, latitude, LONGITUDE, assessment.level(), assessment.score(), assessment.reason())
                : null;
        clock = clock.plusSeconds(1);
        target.onAssessed(telemetry, assessment, record);
    }
}
