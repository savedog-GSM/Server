package com.example.savedog.Telemetry.service;

import com.example.savedog.Report.service.HazardReportTrigger;
import com.example.savedog.Robot.service.DangerResponder;
import com.example.savedog.Safety.service.Assessed;
import com.example.savedog.Safety.service.SafetyService;
import com.example.savedog.Telemetry.DTO.SensorValues;
import com.example.savedog.Telemetry.DTO.TelemetryRequest;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
import com.example.savedog.Telemetry.domain.RobotLocation;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.Telemetry.repository.TelemetryRepository;
import com.example.savedog.global.validation.RequestValidator;
import com.example.savedog.global.websocket.WebSocketService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class TelemetryService {
    private static final double MAX_DISTANCE_CM = 400;
    // 기간을 주지 않았을 때 돌려주는 최근 기록 수
    private static final int DEFAULT_TRACK_SIZE = 1_000;
    // 기간을 넓게 잡아도 한 번에 이보다 많이는 주지 않는다. (1초 주기로 약 2시간 47분 분량)
    private static final int MAX_TRACK_SIZE = 10_000;
    private static final LocalDateTime BEGINNING = LocalDateTime.of(1970, 1, 1, 0, 0);

    private final TelemetryRepository telemetryRepository;
    private final LocationService locationService;
    private final SafetyService safetyService;
    private final WebSocketService webSocketService;
    private final DangerResponder dangerResponder;
    private final HazardReportTrigger hazardReportTrigger;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public TelemetryService(
            TelemetryRepository telemetryRepository,
            LocationService locationService,
            SafetyService safetyService,
            WebSocketService webSocketService,
            DangerResponder dangerResponder,
            HazardReportTrigger hazardReportTrigger,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) {
        this.telemetryRepository = telemetryRepository;
        this.locationService = locationService;
        this.safetyService = safetyService;
        this.webSocketService = webSocketService;
        this.dangerResponder = dangerResponder;
        this.hazardReportTrigger = hazardReportTrigger;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    public TelemetryResponse receive(TelemetryRequest request) {
        double distance = validate(request);
        RobotLocation location = locationService.findRecent().orElse(null);

        // 센서 값 저장과 위험도 판단은 한 트랜잭션으로 묶는다.
        Recorded recorded = transactionTemplate.execute(status -> {
            Telemetry telemetry = telemetryRepository.save(
                    new Telemetry(location, distance)
            );
            return new Recorded(telemetry, safetyService.assess(telemetry));
        });
        TelemetryResponse response = TelemetryResponse.from(recorded.telemetry());
        Assessed assessed = recorded.assessed();

        // 저장이 끝난 뒤의 후속 처리. 여기서 실패해도 센서 값 수신 자체는 성공으로 응답한다.
        runQuietly("실시간 전달", () -> webSocketService.broadcastTelemetry(response));
        runQuietly("위험 시 자동 대응·알림", () -> dangerResponder.onAssessed(response));
        runQuietly("신고 생성 판단", () ->
                hazardReportTrigger.onAssessed(recorded.telemetry(), assessed.assessment(), assessed.record()));
        return response;
    }

    @Transactional(readOnly = true)
    public List<TelemetryResponse> findTrack(LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            List<Telemetry> latest = telemetryRepository.findAllByOrderByRecordedAtDesc(
                    Limit.of(DEFAULT_TRACK_SIZE)
            );
            return latest.stream().map(TelemetryResponse::from).toList();
        }
        LocalDateTime start = from == null ? BEGINNING : from;
        LocalDateTime end = to == null ? LocalDateTime.now(clock) : to;
        if (start.isAfter(end)) {
            throw RequestValidator.invalid("from은 to보다 늦을 수 없습니다.");
        }
        return telemetryRepository
                .findByRecordedAtBetweenOrderByRecordedAtDesc(start, end, Limit.of(MAX_TRACK_SIZE))
                .stream()
                .map(TelemetryResponse::from)
                .toList();
    }

    // 아두이노가 보낸 전방 거리(cm)를 꺼낸다.
    private double validate(TelemetryRequest request) {
        RequestValidator.required(request, "요청 본문");
        SensorValues sensor = RequestValidator.required(request.sensor(), "sensor");
        return RequestValidator.inRange(sensor.distance(), 0, MAX_DISTANCE_CM, "sensor.distance");
    }

    private void runQuietly(String what, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("센서 값은 저장했지만 후속 처리({})에 실패했습니다.", what, e);
        }
    }

    private record Recorded(Telemetry telemetry, Assessed assessed) {
    }
}
