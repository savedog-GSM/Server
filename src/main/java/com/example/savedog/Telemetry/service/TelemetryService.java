package com.example.savedog.Telemetry.service;

import com.example.savedog.Safety.service.SafetyService;
import com.example.savedog.Telemetry.DTO.TelemetryRequest;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
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
    private final TelemetryRepository telemetryRepository;
    private final WebSocketService webSocketService;
    private final SafetyService safetyService;

    @Transactional
    public TelemetryResponse receive(Long robotId, TelemetryRequest request) {
        Telemetry telemetry = telemetryRepository.save(request.toEntity(robotId));
        TelemetryResponse response = TelemetryResponse.from(telemetry);
        webSocketService.broadcastTelemetry(response);
        // TODO(F-05): safetyService.assess(telemetry) 호출을 여기에 연결
        return response;
    }

    @Transactional(readOnly = true)
    public List<TelemetryResponse> findTrack(Long robotId, LocalDateTime from, LocalDateTime to) {
        // TODO(F-03): from/to가 null이면 전체, 있으면 해당 기간의 궤적을 시간순으로 조회
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }
}
