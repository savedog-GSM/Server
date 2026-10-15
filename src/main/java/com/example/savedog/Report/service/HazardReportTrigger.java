package com.example.savedog.Report.service;

import com.example.savedog.Safety.analyzer.SafetyAssessment;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.global.common.GeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;

/**
 * 같은 곳에서 위험이 이어지는지 지켜보다가 조건이 맞으면 신고를 만들게 한다. (기능명세서 F-13)
 * 순간적으로 튄 값 하나로는 신고를 만들지 않기 위한 장치다.
 */
@Slf4j
@Component
public class HazardReportTrigger {
    // 처음 위험이 감지된 지점에서 이 반경 안에 머무는 동안을 "같은 곳"으로 본다.
    private static final double SAME_PLACE_RADIUS_METERS = 15;
    // 위험이 이만큼 이어져야 신고를 만든다.
    private static final Duration MIN_DANGER_DURATION = Duration.ofSeconds(5);
    // 센서 값이 이보다 오래 끊겼다 다시 오면 이어진 것으로 보지 않는다.
    private static final Duration MAX_READING_GAP = Duration.ofSeconds(10);

    private final ReportService reportService;
    // 신고 만들기는 주소 변환·사진 복사처럼 느릴 수 있는 일을 하므로 센서 값 처리와 따로 돌린다.
    private final Executor reportExecutor;

    private GeoPoint startPoint;
    private LocalDateTime startedTime;
    private LocalDateTime lastDangerTime;
    private boolean handled;

    public HazardReportTrigger(
            ReportService reportService,
            @Qualifier("hazardReportExecutor") Executor reportExecutor
    ) {
        this.reportService = reportService;
        this.reportExecutor = reportExecutor;
    }

    // record는 위치가 없어 위험도 기록을 남기지 못했으면 null
    public synchronized void onAssessed(Telemetry telemetry, SafetyAssessment assessment, SafetyRecord record) {
        if (assessment.level() != SafetyLevel.DANGER || record == null) {
            startPoint = null;
            return;
        }
        GeoPoint here = new GeoPoint(record.getLatitude(), record.getLongitude());
        LocalDateTime now = telemetry.getRecordedAt();

        boolean continues = startPoint != null
                && here.distanceTo(startPoint) <= SAME_PLACE_RADIUS_METERS
                && Duration.between(lastDangerTime, now).compareTo(MAX_READING_GAP) <= 0;
        if (!continues) {
            startPoint = here;
            startedTime = now;
            handled = false;
        }
        lastDangerTime = now;

        if (!handled && Duration.between(startedTime, now).compareTo(MIN_DANGER_DURATION) >= 0) {
            // 만들기에 실패해도 같은 위험이 이어지는 동안 매초 다시 시도하지는 않는다.
            handled = true;
            reportExecutor.execute(() -> createReport(telemetry, assessment, record));
        }
    }

    // 다른 스레드에서 돌기 때문에 여기서 잡지 않으면 실패가 아무 데도 남지 않는다.
    private void createReport(Telemetry telemetry, SafetyAssessment assessment, SafetyRecord record) {
        try {
            reportService.createIfNew(telemetry, assessment, record);
        } catch (RuntimeException e) {
            log.error("위험 신고를 만들지 못했습니다. (위험도 기록 {})", record.getId(), e);
        }
    }
}
