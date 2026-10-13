package com.example.savedog.Safety.service;

import com.example.savedog.Robot.service.RobotService;
import com.example.savedog.Safety.DTO.SafetyRecordResponse;
import com.example.savedog.Safety.DTO.SafetySegmentResponse;
import com.example.savedog.Safety.analyzer.SafetyAnalyzer;
import com.example.savedog.Safety.repository.SafetyRecordRepository;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.Telemetry.service.CameraFrameService;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SafetyService {
    private final SafetyAnalyzer safetyAnalyzer;
    private final SafetyRecordRepository safetyRecordRepository;
    private final CameraFrameService cameraFrameService;
    private final RobotService robotService;
    private final WebSocketService webSocketService;

    @Transactional
    public SafetyRecordResponse assess(Telemetry telemetry) {
        // TODO(F-05): 1) cameraFrameService.findRecent(robotId)와 telemetry로 SafetyContext 구성
        //             2) safetyAnalyzer.analyze(context)
        //             3) SafetyRecord 저장 → webSocketService.broadcastSafety(...)
        // TODO(F-09): 판정이 DANGER면 robotService.emergencyStop(robotId)
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    @Transactional(readOnly = true)
    public List<SafetyRecordResponse> findInBounds(double swLat, double swLng, double neLat, double neLng) {
        // TODO(F-06): 지도 영역(남서~북동) 안의 안전도 기록 조회
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    @Transactional(readOnly = true)
    public List<SafetySegmentResponse> findSegments(Long robotId) {
        // TODO(F-06): 시간순 기록을 같은 등급끼리 묶어 구간(선)으로 변환
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }
}
