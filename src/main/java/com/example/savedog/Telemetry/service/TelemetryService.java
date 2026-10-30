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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class TelemetryService {
    private static final double MAX_DISTANCE_CM = 400;
    // 흔한 온습도 센서(DHT11·DHT22)가 잴 수 있는 범위를 넉넉히 덮는다.
    private static final double MIN_TEMPERATURE = -40;
    private static final double MAX_TEMPERATURE = 85;
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
    // 아두이노 값을 마지막으로 받은 뒤 이 시간 동안은 중계 프로그램(로보독 센서)의 거리값을 쓰지 않는다. 0이면 끈다.
    private final Duration arduinoPriority;
    private final AtomicReference<LocalDateTime> lastArduinoAt = new AtomicReference<>();

    public TelemetryService(
            TelemetryRepository telemetryRepository,
            LocationService locationService,
            SafetyService safetyService,
            WebSocketService webSocketService,
            DangerResponder dangerResponder,
            HazardReportTrigger hazardReportTrigger,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${savedog.telemetry.arduino-priority:3s}") Duration arduinoPriority
    ) {
        this.telemetryRepository = telemetryRepository;
        this.locationService = locationService;
        this.safetyService = safetyService;
        this.webSocketService = webSocketService;
        this.dangerResponder = dangerResponder;
        this.hazardReportTrigger = hazardReportTrigger;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.arduinoPriority = arduinoPriority;
    }

    /**
     * 중계 프로그램이 보낸 로보독 센서 값(POST /api/robot/telemetry).
     * 아두이노와 로보독이 둘 다 전방 거리를 보내므로, 아두이노가 최근에 보냈으면 이 값은 저장하지 않고 비어 있는 값을 돌려준다.
     * (두 센서 값이 섞이면 위험·안전이 번갈아 나와 경고가 깜빡이고, 위험이 이어져야 만드는 신고가 만들어지지 않는다)
     * 아두이노가 끊기면 다시 이 값을 쓴다.
     */
    public Optional<TelemetryResponse> receive(TelemetryRequest request) {
        RequestValidator.required(request, "요청 본문");
        if (arduinoIsActive()) {
            return Optional.empty();
        }
        return Optional.of(record(validate(RequestValidator.required(request.sensor(), "sensor"), "sensor.")));
    }

    // 아두이노 전용 주소(POST /api/arduino/sensors)로 받은 값. 항목이 sensor로 감싸여 있지 않다.
    public TelemetryResponse receiveArduino(SensorValues values) {
        RequestValidator.required(values, "요청 본문");
        SensorValues sensor = validate(values, "");
        lastArduinoAt.set(LocalDateTime.now(clock));
        return record(sensor);
    }

    private boolean arduinoIsActive() {
        LocalDateTime last = lastArduinoAt.get();
        return arduinoPriority.isPositive() && last != null
                && Duration.between(last, LocalDateTime.now(clock)).compareTo(arduinoPriority) <= 0;
    }

    private TelemetryResponse record(SensorValues sensor) {
        double distance = sensor.distance();
        RobotLocation location = locationService.findRecent().orElse(null);

        // 센서 값 저장과 위험도 판단은 한 트랜잭션으로 묶는다.
        Recorded recorded = transactionTemplate.execute(status -> {
            Telemetry telemetry = telemetryRepository.save(
                    new Telemetry(location, distance, sensor.temperature(), sensor.humidity())
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

    // 전방 거리(cm)는 꼭 있어야 하고, 온습도는 보냈을 때만 범위를 확인한다.
    // prefix는 오류 메시지에 쓰는 항목 이름 앞부분. (/api/robot/telemetry는 "sensor.", 아두이노 주소는 "")
    private SensorValues validate(SensorValues sensor, String prefix) {
        double distance = RequestValidator.inRange(sensor.distance(), 0, MAX_DISTANCE_CM, prefix + "distance");
        Double temperature = sensor.temperature() == null ? null
                : RequestValidator.inRange(sensor.temperature(), MIN_TEMPERATURE, MAX_TEMPERATURE, prefix + "temperature");
        Double humidity = sensor.humidity() == null ? null
                : RequestValidator.inRange(sensor.humidity(), 0, 100, prefix + "humidity");
        return new SensorValues(distance, temperature, humidity);
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
