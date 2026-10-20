package com.example.savedog.Safety.service;

import com.example.savedog.Safety.DTO.SafetyRecordResponse;
import com.example.savedog.Safety.DTO.SafetySegmentResponse;
import com.example.savedog.Safety.analyzer.SafetyAnalyzer;
import com.example.savedog.Safety.analyzer.SafetyAssessment;
import com.example.savedog.Safety.analyzer.SafetyContext;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.Safety.repository.SafetyRecordRepository;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SafetyService {
    // 지도 한 화면에 내려주는 기록의 상한. 넘으면 최근 것부터 이만큼만 준다.
    private static final int MAX_RECORDS_IN_BOUNDS = 5_000;
    // 구간으로 묶어 내려주는 기록의 상한. 1초에 한 건씩이면 5시간 반쯤 된다.
    private static final int MAX_RECORDS_FOR_SEGMENTS = 20_000;

    private final SafetyAnalyzer safetyAnalyzer;
    private final SafetyRecordRepository safetyRecordRepository;

    // 센서 값 저장과 같은 트랜잭션 안에서 불린다.
    @Transactional
    public Assessed assess(Telemetry telemetry) {
        GeoPoint location = telemetry.hasLocation()
                ? new GeoPoint(telemetry.getLatitude(), telemetry.getLongitude())
                : null;
        SafetyAssessment assessment = safetyAnalyzer.analyze(
                new SafetyContext(location, telemetry.getDistance())
        );
        telemetry.applyRisk(assessment.level(), assessment.score(), assessment.reason());

        // 위치 없는 결과는 지도에 찍을 수 없으므로 위험도 기록으로는 남기지 않는다.
        SafetyRecord record = null;
        if (location != null) {
            record = safetyRecordRepository.save(new SafetyRecord(
                    telemetry.getId(),
                    location.latitude(),
                    location.longitude(),
                    assessment.level(),
                    assessment.score(),
                    assessment.reason()
            ));
        }
        return new Assessed(assessment, record);
    }

    @Transactional(readOnly = true)
    public List<SafetyRecordResponse> findInBounds(double swLat, double swLng, double neLat, double neLng) {
        GeoPoint southWest = RequestValidator.coordinate(swLat, swLng, "남서 좌표(sw)");
        GeoPoint northEast = RequestValidator.coordinate(neLat, neLng, "북동 좌표(ne)");
        if (southWest.latitude() > northEast.latitude() || southWest.longitude() > northEast.longitude()) {
            throw RequestValidator.invalid("남서 좌표(sw)는 북동 좌표(ne)보다 남쪽·서쪽이어야 합니다.");
        }
        return safetyRecordRepository
                .findByLatitudeBetweenAndLongitudeBetweenOrderByAssessedAtDesc(
                        southWest.latitude(), northEast.latitude(),
                        southWest.longitude(), northEast.longitude(),
                        Limit.of(MAX_RECORDS_IN_BOUNDS)
                )
                .stream()
                .map(SafetyRecordResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SafetySegmentResponse> findSegments() {
        // 최근 것부터 상한까지만 읽은 뒤, 구간을 만들 수 있게 시간순으로 되돌린다.
        List<SafetyRecord> records = new ArrayList<>(
                safetyRecordRepository.findAllByOrderByAssessedAtDesc(Limit.of(MAX_RECORDS_FOR_SEGMENTS))
        );
        Collections.reverse(records);
        return SafetySegmentBuilder.build(records);
    }
}
