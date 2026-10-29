package com.example.savedog.Pathfinding.service;

import com.example.savedog.Pathfinding.DTO.CoordinateRequest;
import com.example.savedog.Pathfinding.DTO.PathSearchRequest;
import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.planner.PathPlanner;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.Safety.repository.SafetyRecordRepository;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PathfindingService {
    // 출발지·도착지 바깥으로 이만큼까지 돌아가는 길을 허용한다.
    private static final double SEARCH_MARGIN_METERS = 1_000;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;
    // 한 번의 탐색에 쓰는 기록의 상한. 넘으면 최근 것부터 이만큼만 쓴다.
    private static final int MAX_RECORDS_FOR_SEARCH = 50_000;

    private final PathPlanner pathPlanner;
    private final SafetyRecordRepository safetyRecordRepository;

    @Transactional(readOnly = true)
    public PathSearchResponse search(PathSearchRequest request) {
        RequestValidator.required(request, "요청 본문");
        GeoPoint start = toPoint(request.start(), "start");
        GeoPoint end = toPoint(request.end(), "end");
        PathMode mode = request.mode() == null ? PathMode.SAFEST : request.mode();

        return plan(start, end, mode).orElseThrow(() -> new BusinessException(ErrorCode.PATH_NOT_FOUND));
    }

    // 갈 수 있는 경로가 없으면 Optional.empty(). 여러 도착 후보를 차례로 시험할 때 쓴다.
    @Transactional(readOnly = true)
    public Optional<PathSearchResponse> plan(GeoPoint start, GeoPoint end, PathMode mode) {
        return pathPlanner.plan(start, end, mode, recordsAround(start, end))
                .map(path -> PathSearchResponse.of(mode, path));
    }

    /**
     * 출발지와 도착지를 감싸는 사각형에 여유를 둔 범위의 기록만 읽는다.
     * 쌓인 기록 전부를 매번 메모리에 올리면 기록이 늘수록 느려지다 결국 서버가 멈춘다.
     */
    private List<SafetyRecord> recordsAround(GeoPoint start, GeoPoint end) {
        double south = Math.min(start.latitude(), end.latitude());
        double north = Math.max(start.latitude(), end.latitude());
        double west = Math.min(start.longitude(), end.longitude());
        double east = Math.max(start.longitude(), end.longitude());

        double latitudeMargin = SEARCH_MARGIN_METERS / METERS_PER_LATITUDE_DEGREE;
        // 경도 1도의 길이는 위도가 높을수록 짧아진다. 극지방에서 0으로 나누지 않게 아래를 막는다.
        double widestLatitude = Math.max(Math.abs(south), Math.abs(north));
        double longitudeMargin = latitudeMargin / Math.max(Math.cos(Math.toRadians(widestLatitude)), 0.01);

        List<SafetyRecord> records = safetyRecordRepository
                .findByLatitudeBetweenAndLongitudeBetweenOrderByAssessedAtDesc(
                        Math.max(south - latitudeMargin, -90), Math.min(north + latitudeMargin, 90),
                        Math.max(west - longitudeMargin, -180), Math.min(east + longitudeMargin, 180),
                        Limit.of(MAX_RECORDS_FOR_SEARCH)
                );
        if (records.size() == MAX_RECORDS_FOR_SEARCH) {
            log.warn("경로 탐색 범위의 기록이 {}건을 넘어 최근 기록만 사용합니다.", MAX_RECORDS_FOR_SEARCH);
        }
        return records;
    }

    private GeoPoint toPoint(CoordinateRequest coordinate, String name) {
        RequestValidator.required(coordinate, name);
        return RequestValidator.coordinate(coordinate.latitude(), coordinate.longitude(), name);
    }
}
