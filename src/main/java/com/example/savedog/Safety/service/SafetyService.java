package com.example.savedog.Safety.service;

import com.example.savedog.Robot.service.RobotService;
import com.example.savedog.Safety.DTO.RiskResponse;
import com.example.savedog.Safety.DTO.SafetyRecordResponse;
import com.example.savedog.Safety.DTO.SafetySegmentResponse;
import com.example.savedog.Safety.analyzer.SafetyAnalyzer;
import com.example.savedog.Safety.repository.SafetyRecordRepository;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SafetyService {
    private final SafetyAnalyzer safetyAnalyzer;
    private final SafetyRecordRepository safetyRecordRepository;
    private final RobotService robotService;

    @Transactional
    public RiskResponse assess(Telemetry telemetry) {
        // TODO(F-06): 1) telemetry로 SafetyContext 구성 → safetyAnalyzer.analyze(context)
        //             2) 위치가 있는 기록일 때만 SafetyRecord 저장 (위치 없는 결과는 지도 데이터에 넣지 않음)
        //             3) 판정 결과를 RiskResponse로 반환
        // TODO(F-12): 등급이 DANGER로 바뀌는 순간 한 번 robotService.emergencyStop()
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    @Transactional(readOnly = true)
    public List<SafetyRecordResponse> findInBounds(double swLat, double swLng, double neLat, double neLng) {
        // TODO(F-07): 지도 영역(남서~북동) 안의 위험도 기록 조회
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    @Transactional(readOnly = true)
    public List<SafetySegmentResponse> findSegments() {
        // TODO(F-07): 시간순 기록을 같은 등급끼리 묶어 구간(선)으로 변환
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }
}
