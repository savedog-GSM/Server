package com.example.savedog.Safety.service;

import com.example.savedog.Robot.service.RobotService;
import com.example.savedog.Safety.DTO.RiskResponse;
import com.example.savedog.Safety.DTO.SafetyRecordResponse;
import com.example.savedog.Safety.DTO.SafetySegmentResponse;
import com.example.savedog.Safety.analyzer.SafetyAnalyzer;
import com.example.savedog.Safety.analyzer.SafetyAssessment;
import com.example.savedog.Safety.analyzer.SafetyContext;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.Safety.repository.SafetyRecordRepository;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.global.common.GeoPoint;
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
        GeoPoint location = locationOf(telemetry);
        SafetyAssessment assessment = safetyAnalyzer.analyze(
                new SafetyContext(location, telemetry.getDistance(), telemetry.getRoll(), telemetry.getPitch())
        );
        // 위치 없는 결과는 지도에 찍을 수 없으므로 기록으로 남기지 않는다.
        if (location != null) {
            safetyRecordRepository.save(new SafetyRecord(
                    telemetry.getId(),
                    location.latitude(),
                    location.longitude(),
                    assessment.level(),
                    assessment.score(),
                    assessment.reason()
            ));
        }
        // TODO(F-12): 등급이 DANGER로 바뀌는 순간 한 번 robotService.emergencyStop()
        return new RiskResponse(assessment.level(), assessment.score(), assessment.reason());
    }

    private GeoPoint locationOf(Telemetry telemetry) {
        if (telemetry.getLatitude() == null || telemetry.getLongitude() == null) {
            return null;
        }
        return new GeoPoint(telemetry.getLatitude(), telemetry.getLongitude());
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
