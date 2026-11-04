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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 좌표는 A(0,0)에서 북쪽/동쪽으로 몇 m인지로 적는다. 도착지 B는 동쪽 60m(0,60).
 * 로보독은 5m마다 1초 간격으로 기록을 남긴 것처럼 만든다.
 */
class AStarPathPlannerTest {
    private static final double BASE_LATITUDE = 37.5665;
    private static final double BASE_LONGITUDE = 126.9780;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;
    private static final int SAFE = 1;
    private static final int DANGER = 5;

    private final AStarPathPlanner planner = new AStarPathPlanner();
    private final List<SafetyRecord> records = new ArrayList<>();
    private LocalDateTime clock = LocalDateTime.of(2026, 10, 13, 14, 0);

    @Test
    @DisplayName("기록이 하나도 없어도 위험도를 3점으로 보고 경로를 찾는다")
    void noRecordsStillFindsPath() {
        PlannedPath path = planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records).orElseThrow();

        assertEquals(60, path.distanceMeters(), 0.5);
        assertEquals(AStarPathPlanner.UNKNOWN_RISK_SCORE, path.averageRiskScore());
        assertEquals(SafetyLevel.CAUTION, path.maxRiskLevel());
        assertEquals(60, path.uncollectedMeters(), 10.5);
        // 곧은 길은 꺾이는 곳만 남기므로 출발지와 도착지 두 점이다.
        assertEquals(List.of(at(0, 0), at(0, 60)), path.points());
    }

    @Test
    @DisplayName("수집된 안전한 길이 있으면 조금 멀어도 그 길로 간다 (모르는 길 60m × 3 > 안전한 길 120m × 1)")
    void prefersCollectedSafeDetour() {
        walk(0, 0, 30, 0, SAFE);
        walk(30, 0, 30, 60, SAFE);
        walk(30, 60, 0, 60, SAFE);

        PlannedPath path = planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records).orElseThrow();

        assertEquals(120, path.distanceMeters(), 20);
        assertEquals(SafetyLevel.SAFE, path.maxRiskLevel());
        assertTrue(path.uncollectedMeters() <= 15, "모르는 구간 " + path.uncollectedMeters() + "m");
    }

    @Test
    @DisplayName("수집된 길이 너무 멀리 돌아가면 모르는 길로 곧장 간다 (60m × 3 < 260m × 1)")
    void takesUnknownShortcutOverLongDetour() {
        walk(0, 0, 100, 0, SAFE);
        walk(100, 0, 100, 60, SAFE);
        walk(100, 60, 0, 60, SAFE);

        PlannedPath path = planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records).orElseThrow();

        assertEquals(60, path.distanceMeters(), 5);
        assertTrue(path.uncollectedMeters() >= 40, "모르는 구간 " + path.uncollectedMeters() + "m");
    }

    @Test
    @DisplayName("안전 모드는 위험 칸을 피해 옆으로 돌아가고, 최단 모드는 그대로 지나간다")
    void dangerIsAvoidedOnlyInSafestMode() {
        walkDirectRoad(DANGER);

        PlannedPath safest = planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records).orElseThrow();
        PlannedPath shortest = planner.plan(at(0, 0), at(0, 60), PathMode.SHORTEST, records).orElseThrow();

        assertNotEquals(SafetyLevel.DANGER, safest.maxRiskLevel());
        assertTrue(safest.distanceMeters() > 60, "돌아간 거리 " + safest.distanceMeters());
        assertEquals(60, shortest.distanceMeters(), 0.5);
        assertEquals(SafetyLevel.DANGER, shortest.maxRiskLevel());
    }

    @Test
    @DisplayName("같은 길을 다시 지나갔을 때 안전해졌으면 최근 회차를 더 무겁게 보고 그 길로 간다")
    void latestPassOutweighsOlderPass() {
        walkDirectRoad(DANGER);
        pause(120);
        walkDirectRoad(SAFE);

        PlannedPath path = planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records).orElseThrow();

        // 가운데 칸: (최근 1점 × 1 + 이전 5점 × 0.5) ÷ 1.5 = 2.3점 → 안전 등급
        assertEquals(SafetyLevel.SAFE, path.maxRiskLevel());
        assertEquals(60, path.distanceMeters(), 0.5);
    }

    @Test
    @DisplayName("도착지 칸이 위험 등급이어도 그곳까지는 안내한다")
    void dangerousDestinationIsStillReachable() {
        walk(0, 55, 0, 65, DANGER);

        assertTrue(planner.plan(at(0, 0), at(0, 60), PathMode.SAFEST, records).isPresent());
    }

    @Test
    @DisplayName("너무 먼 거리(칸 100만 개 초과)는 찾지 않는다")
    void tooFar() {
        assertTrue(planner.plan(at(0, 0), at(12_000, 12_000), PathMode.SAFEST, records).isEmpty());
        assertTrue(planner.plan(at(0, 0), at(1_000, 1_000), PathMode.SAFEST, records).isPresent());
    }

    // 직선 길 A→B. 가운데(20~40m)만 middleRiskScore
    private void walkDirectRoad(int middleRiskScore) {
        walk(0, 0, 0, 15, SAFE);
        walk(0, 20, 0, 40, middleRiskScore);
        walk(0, 45, 0, 60, SAFE);
    }

    // 두 지점 사이를 5m마다 1초 간격으로 지나가며 기록을 남긴다.
    private void walk(double fromNorth, double fromEast, double toNorth, double toEast, int riskScore) {
        double length = Math.hypot(toNorth - fromNorth, toEast - fromEast);
        int steps = (int) Math.round(length / 5);
        for (int i = 0; i <= steps; i++) {
            double ratio = steps == 0 ? 0 : (double) i / steps;
            visit(fromNorth + (toNorth - fromNorth) * ratio, fromEast + (toEast - fromEast) * ratio, riskScore);
        }
    }

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
                + eastMeters / (METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(BASE_LATITUDE)));
        return new GeoPoint(latitude, longitude);
    }
}
