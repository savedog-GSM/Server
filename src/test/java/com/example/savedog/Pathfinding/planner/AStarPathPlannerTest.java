package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.global.common.GeoPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 시험용 길 (좌표는 A에서 북쪽/동쪽으로 몇 m인지)
 *   직선 길: A(0,0) → 동쪽으로 12m씩 → B(0,60)          길이 60m
 *   우회 길: A → 북쪽 24m → 동쪽 60m → 남쪽 24m → B      길이 108m
 */
class AStarPathPlannerTest {
    private static final double BASE_LATITUDE = 37.5665;
    private static final double BASE_LONGITUDE = 126.9780;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;
    private static final int SAFE = 1;
    private static final int CAUTION = 4;
    private static final int DANGER = 5;

    private final AStarPathPlanner planner = new AStarPathPlanner();
    private final List<SafetyRecord> records = new ArrayList<>();
    private LocalDateTime clock = LocalDateTime.of(2026, 10, 13, 14, 0);

    @Test
    @DisplayName("최단 모드는 위험 구간을 지나더라도 가장 짧은 길을 고른다")
    void shortestTakesDirectRoadThroughDanger() {
        walkDirectRoad(DANGER);
        pause(120);
        walkDetour();

        PlannedPath path = planner.plan(at(0, 0), at(0, 60), PathMode.SHORTEST, records).orElseThrow();

        assertEquals(60, path.distanceMeters(), 0.5);
        assertEquals(6, path.points().size());
        assertEquals(SafetyLevel.DANGER, path.maxRiskLevel());
    }

    @Test
    @DisplayName("안전 모드는 위험 구간을 피해 돌아간다")
    void safestTakesDetourAroundDanger() {
        walkDirectRoad(DANGER);
        pause(120);
        walkDetour();

        PlannedPath path = planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records).orElseThrow();

        assertEquals(108, path.distanceMeters(), 0.5);
        assertEquals(SafetyLevel.SAFE, path.maxRiskLevel());
        assertEquals(1, path.averageRiskScore());
    }

    @Test
    @DisplayName("안전 모드는 주의 구간을 지날 수는 있지만 비용이 커서 안전한 우회로가 있으면 돌아간다")
    void safestPrefersDetourOverCautionRoad() {
        walkDirectRoad(CAUTION);
        pause(120);
        walkDetour();

        PlannedPath path = planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records).orElseThrow();

        assertEquals(108, path.distanceMeters(), 0.5);
    }

    @Test
    @DisplayName("안전 모드에서 위험 구간을 피할 길이 없으면 경로가 없다")
    void safestFindsNothingWhenOnlyRoadIsDangerous() {
        walkDirectRoad(DANGER);

        assertTrue(planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records).isEmpty());
        assertTrue(planner.plan(at(0, 0), at(0, 60), PathMode.SHORTEST, records).isPresent());
    }

    @Test
    @DisplayName("같은 길을 다시 지나갔을 때 안전해졌으면 최근 회차를 더 무겁게 본다")
    void latestPassOutweighsOlderPass() {
        walkDirectRoad(DANGER);
        pause(120);
        walkDirectRoad(SAFE);

        PlannedPath path = planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records).orElseThrow();

        // 가운데 두 칸: (최근 1점 × 1 + 이전 5점 × 0.5) ÷ 1.5 = 2.3점 → 안전 등급
        assertEquals(SafetyLevel.SAFE, path.maxRiskLevel());
        assertEquals(1.4, path.averageRiskScore());
    }

    @Test
    @DisplayName("기록 사이가 10초 넘게 비면 이어진 길로 보지 않는다")
    void longGapBetweenRecordsBreaksTheRoad() {
        visit(0, 0, SAFE);
        visit(0, 12, SAFE);
        pause(30);
        visit(0, 24, SAFE);

        assertTrue(planner.plan(at(0, 0), at(0, 24), PathMode.SHORTEST, records).isEmpty());
        assertTrue(planner.plan(at(0, 0), at(0, 12), PathMode.SHORTEST, records).isPresent());
    }

    @Test
    @DisplayName("출발지에서 30m 안에 다닌 길이 없으면 경로가 없다")
    void startTooFarFromAnyRoad() {
        walkDirectRoad(SAFE);

        assertTrue(planner.plan(at(100, 0), at(0, 60), PathMode.SHORTEST, records).isEmpty());
        assertTrue(planner.plan(at(20, 0), at(0, 60), PathMode.SHORTEST, records).isPresent());
    }

    @Test
    @DisplayName("쌓인 기록이 없으면 경로가 없다")
    void noRecords() {
        Optional<PlannedPath> path = planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records);

        assertTrue(path.isEmpty());
    }

    private void walkDirectRoad(int middleRiskScore) {
        visit(0, 0, SAFE);
        visit(0, 12, SAFE);
        visit(0, 24, middleRiskScore);
        visit(0, 36, middleRiskScore);
        visit(0, 48, SAFE);
        visit(0, 60, SAFE);
    }

    private void walkDetour() {
        visit(0, 0, SAFE);
        visit(12, 0, SAFE);
        for (int east = 0; east <= 60; east += 12) {
            visit(24, east, SAFE);
        }
        visit(12, 60, SAFE);
        visit(0, 60, SAFE);
    }

    // 로보독이 1초 간격으로 한 지점씩 지나가며 기록을 남긴 것처럼 만든다.
    private void visit(double northMeters, double eastMeters, int riskScore) {
        GeoPoint point = at(northMeters, eastMeters);
        SafetyRecord record = new SafetyRecord(
                null, point.latitude(), point.longitude(),
                SafetyLevel.fromRiskScore(riskScore), riskScore, null
        );
        ReflectionTestUtils.setField(record, "assessedAt", clock);
        records.add(record);
        clock = clock.plusSeconds(1);
    }

    private void pause(long seconds) {
        clock = clock.plusSeconds(seconds);
    }

    private GeoPoint at(double northMeters, double eastMeters) {
        double latitude = BASE_LATITUDE + northMeters / METERS_PER_LATITUDE_DEGREE;
        double longitude = BASE_LONGITUDE
                + eastMeters / (METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(latitude)));
        return new GeoPoint(latitude, longitude);
    }
}
