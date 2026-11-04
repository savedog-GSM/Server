package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.osm.WalkingRouteSource;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.global.common.GeoPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 가짜 걷기 경로 서버로 확인한다. 좌표는 A(0,0)에서 북쪽/동쪽으로 몇 m인지로 적고, 도착지 B는 동쪽 100m(0,100).
 *   곧은 길: A → B                             100m
 *   북쪽 길: A → (40,0) → (40,100) → B         180m
 */
class RoadPathPlannerTest {
    private static final double BASE_LATITUDE = 37.5665;
    private static final double BASE_LONGITUDE = 126.9780;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;
    private static final GeoPoint A = at(0, 0);
    private static final GeoPoint B = at(0, 100);
    private static final List<GeoPoint> STRAIGHT = List.of(A, at(0, 50), B);
    private static final List<GeoPoint> NORTH = List.of(A, at(40, 0), at(40, 100), B);

    private final FakeRoutes routes = new FakeRoutes();
    private final RoadPathPlanner planner = new RoadPathPlanner(routes, new AStarPathPlanner());
    private final List<SafetyRecord> records = new ArrayList<>();
    private LocalDateTime clock = LocalDateTime.of(2026, 10, 13, 14, 0);

    @Test
    @DisplayName("도로를 따라간 경로를 돌려주고, 기록이 없으면 3점으로 본다")
    void followsRoadsWithUnknownRisk() {
        routes.answer = List.of(STRAIGHT);

        PlannedPath path = planner.plan(A, B, PathMode.SAFEST, records).orElseThrow();

        assertTrue(path.followsRoads());
        assertEquals(100, path.distanceMeters(), 0.5);
        assertEquals(3, path.averageRiskScore());
        assertEquals(SafetyLevel.CAUTION, path.maxRiskLevel());
        assertEquals(100, path.uncollectedMeters(), 0.5);
        assertEquals(A, path.points().getFirst());
        assertEquals(B, path.points().getLast());
    }

    @Test
    @DisplayName("안전 우선은 길이 × 위험 점수가 작은 후보를 고른다: 주의 길 100m(×4)보다 안전한 길 180m(×1)")
    void safestPicksLowerRiskWeightedCandidate() {
        routes.answer = List.of(STRAIGHT, NORTH);
        walk(0, 0, 0, 100, 4);
        walk(0, 0, 40, 0, 1);
        walk(40, 0, 40, 100, 1);
        walk(40, 100, 0, 100, 1);

        PlannedPath safest = planner.plan(A, B, PathMode.SAFEST, records).orElseThrow();
        PlannedPath shortest = planner.plan(A, B, PathMode.SHORTEST, records).orElseThrow();

        assertEquals(180, safest.distanceMeters(), 1);
        // 출발 칸에는 두 길의 기록이 섞여 있어(4점·1점) 그 칸만 주의일 수 있다. 나머지는 1점이다.
        assertTrue(safest.averageRiskScore() < 1.5, "평균 " + safest.averageRiskScore());
        assertEquals(100, shortest.distanceMeters(), 1);
    }

    @Test
    @DisplayName("안전 우선은 위험 칸을 지나는 후보를 빼고, 최단은 그대로 고른다")
    void safestSkipsCandidatesThroughDanger() {
        routes.answer = List.of(STRAIGHT, NORTH);
        walk(0, 45, 0, 55, 5);

        PlannedPath safest = planner.plan(A, B, PathMode.SAFEST, records).orElseThrow();
        PlannedPath shortest = planner.plan(A, B, PathMode.SHORTEST, records).orElseThrow();

        assertEquals(180, safest.distanceMeters(), 1);
        assertFalse(safest.maxRiskLevel() == SafetyLevel.DANGER);
        assertEquals(SafetyLevel.DANGER, shortest.maxRiskLevel());
    }

    @Test
    @DisplayName("후보가 모두 위험 칸을 지나면 위험 지점 양옆을 거쳐 가는 경로를 더 받아 그중 안전한 것을 고른다")
    void triesDetoursAroundDanger() {
        walk(0, 45, 0, 55, 5);
        GeoPoint southVia = at(-40, 50);
        routes.answer = List.of(STRAIGHT);
        // 거쳐 갈 곳을 주면, 남쪽으로 40m 돌아가는 길만 실제로 있다고 하자.
        routes.viaAnswer = via -> via.distanceTo(southVia) < 5
                ? List.of(List.of(A, at(-40, 0), at(-40, 100), B))
                : List.of(STRAIGHT);

        PlannedPath path = planner.plan(A, B, PathMode.SAFEST, records).orElseThrow();

        assertEquals(180, path.distanceMeters(), 1);
        assertFalse(path.maxRiskLevel() == SafetyLevel.DANGER);
        assertEquals(4, routes.viaRequests.size());
    }

    @Test
    @DisplayName("우회 경로도 모두 위험 칸을 지나면 경로가 없다")
    void noSafeDetour() {
        walk(0, 45, 0, 55, 5);
        routes.answer = List.of(STRAIGHT);
        routes.viaAnswer = via -> List.of(STRAIGHT);

        assertTrue(planner.plan(A, B, PathMode.SAFEST, records).isEmpty());
    }

    @Test
    @DisplayName("도착지 바로 옆(15m 안)의 위험 칸은 막지 않는다")
    void dangerAtDestinationIsAllowed() {
        routes.answer = List.of(STRAIGHT);
        walk(0, 95, 0, 100, 5);

        assertTrue(planner.plan(A, B, PathMode.SAFEST, records).isPresent());
    }

    @Test
    @DisplayName("걷기 경로 서버에 닿지 못하거나 이어진 도로가 없으면 도로 정보 없이 칸으로 찾는다")
    void fallsBackToGrid() {
        routes.failing = true;
        PlannedPath whenDown = planner.plan(A, B, PathMode.SAFEST, records).orElseThrow();
        assertFalse(whenDown.followsRoads());

        routes.failing = false;
        routes.answer = List.of();
        PlannedPath whenNoRoad = planner.plan(A, B, PathMode.SAFEST, records).orElseThrow();
        assertFalse(whenNoRoad.followsRoads());
    }

    private void walk(double fromNorth, double fromEast, double toNorth, double toEast, int riskScore) {
        double length = Math.hypot(toNorth - fromNorth, toEast - fromEast);
        int steps = (int) Math.round(length / 5);
        for (int i = 0; i <= steps; i++) {
            double ratio = steps == 0 ? 0 : (double) i / steps;
            GeoPoint point = at(fromNorth + (toNorth - fromNorth) * ratio, fromEast + (toEast - fromEast) * ratio);
            SafetyRecord record = new SafetyRecord(null, point.latitude(), point.longitude(),
                    SafetyLevel.fromRiskScore(riskScore), riskScore, null);
            ReflectionTestUtils.setField(record, "assessedAt", clock);
            records.add(record);
            clock = clock.plusSeconds(1);
        }
    }

    private static GeoPoint at(double northMeters, double eastMeters) {
        return new GeoPoint(
                BASE_LATITUDE + northMeters / METERS_PER_LATITUDE_DEGREE,
                BASE_LONGITUDE + eastMeters / (METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(BASE_LATITUDE)))
        );
    }

    private static class FakeRoutes implements WalkingRouteSource {
        private List<List<GeoPoint>> answer = List.of();
        private java.util.function.Function<GeoPoint, List<List<GeoPoint>>> viaAnswer = via -> List.of();
        private final List<GeoPoint> viaRequests = new CopyOnWriteArrayList<>();
        private boolean failing;

        @Override
        public List<List<GeoPoint>> routes(List<GeoPoint> waypoints, boolean alternatives) {
            if (failing) {
                throw new WalkingRouteUnavailableException("down", null);
            }
            if (waypoints.size() == 3) {
                viaRequests.add(waypoints.get(1));
                return viaAnswer.apply(waypoints.get(1));
            }
            return answer;
        }
    }
}
