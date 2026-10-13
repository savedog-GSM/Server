package com.example.savedog.Telemetry.service;

import com.example.savedog.Safety.DTO.RiskResponse;
import com.example.savedog.Safety.service.SafetyService;
import com.example.savedog.Telemetry.DTO.SensorValues;
import com.example.savedog.Telemetry.DTO.TelemetryRequest;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
import com.example.savedog.Telemetry.domain.RobotLocation;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.Telemetry.repository.TelemetryRepository;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryService {
    private static final double MAX_DISTANCE_CM = 400;
    private static final double MAX_TILT_DEGREES = 180;

    private final TelemetryRepository telemetryRepository;
    private final LocationService locationService;
    private final WebSocketService webSocketService;
    private final SafetyService safetyService;

    @Transactional
    public TelemetryResponse receive(TelemetryRequest request) {
        SensorValues sensor = validate(request);
        RobotLocation location = locationService.findRecent().orElse(null);
        Telemetry telemetry = telemetryRepository.save(
                new Telemetry(location, sensor.distance(), sensor.roll(), sensor.pitch())
        );
        RiskResponse risk = safetyService.assess(telemetry);
        TelemetryResponse response = TelemetryResponse.of(telemetry, risk);
        webSocketService.broadcastTelemetry(response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<TelemetryResponse> findTrack(LocalDateTime from, LocalDateTime to) {
        // TODO(F-05): from/to가 있으면 그 기간, 없으면 최근 1,000건을 시간순으로 조회
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    private SensorValues validate(TelemetryRequest request) {
        SensorValues sensor = request.sensor();
        if (sensor == null || sensor.distance() == null || sensor.roll() == null || sensor.pitch() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        boolean inRange = sensor.distance() >= 0 && sensor.distance() <= MAX_DISTANCE_CM
                && Math.abs(sensor.roll()) <= MAX_TILT_DEGREES
                && Math.abs(sensor.pitch()) <= MAX_TILT_DEGREES;
        if (!inRange) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return sensor;
    }
}
