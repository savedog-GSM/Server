package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.osm.WalkingRouteSource;
import com.example.savedog.Pathfinding.osm.WalkingRouteSource.WalkingRouteUnavailableException;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.global.common.GeoPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 도로를 따라가는 경로를 찾는다. (기능명세서 F-09, F-10)
 * 1) OpenStreetMap 걷기 경로 서버에서 후보 경로를 받는다.
 * 2) 후보마다 5m 간격으로 위험도를 매긴다. 로보독이 지나간 칸은 기록 점수, 안 지나간 칸은 3점.
 * 3) 안전 우선은 위험 칸(4.5점 이상)을 지나는 후보를 빼고 "길이 × 위험 점수"가 가장 작은 것을, 최단은 가장 짧은 것을 고른다.
 * 4) 안전 우선인데 후보가 모두 위험 칸을 지나면, 첫 위험 지점 양옆으로 40·80m 떨어진 곳을 거쳐 가는 경로를 더 받아 본다.
 * 걷기 경로 서버를 쓸 수 없으면 도로 정보 없이 칸으로 찾는다. (AStarPathPlanner, followsRoads=false)
 */
@Slf4j
@Primary
@Component
public class RoadPathPlanner implements PathPlanner {
    private static final double SAMPLE_METERS = 5;
    private static final double MIN_RISK_SCORE = 1;
    // 출발지·도착지 바로 옆의 위험 칸은 이미 있거나 가야 하는 곳이므로 막지 않는다.
    private static final double ENDPOINT_EXEMPT_METERS = 15;
    private static final double[] DETOUR_OFFSETS_METERS = {40, -40, 80, -80};
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;

    private final WalkingRouteSource walkingRoutes;
    private final AStarPathPlanner gridPlanner;

    public RoadPathPlanner(WalkingRouteSource walkingRoutes, AStarPathPlanner gridPlanner) {
        this.walkingRoutes = walkingRoutes;
        this.gridPlanner = gridPlanner;
    }

    @Override
    public Optional<PlannedPath> plan(GeoPoint start, GeoPoint end, PathMode mode, List<SafetyRecord> safetyRecords) {
        if (!walkingRoutes.available()) {
            return gridPlanner.plan(start, end, mode, safetyRecords);
        }
        RiskCells risks = new RiskCells(safetyRecords, (start.latitude() + end.latitude()) / 2);
        try {
            List<Scored> candidates = scoreAll(walkingRoutes.routes(List.of(start, end), true), start, end, risks);
            if (candidates.isEmpty()) {
                // 도로망에서 이어지지 않는 곳(섬, 공사장 안 등)이면 칸으로라도 찾아 본다.
                return gridPlanner.plan(start, end, mode, safetyRecords);
            }
            if (mode == PathMode.SHORTEST) {
                return Optional.of(best(candidates, mode).path());
            }
            Optional<Scored> safe = safest(candidates);
            if (safe.isEmpty()) {
                safe = safest(scoreAll(detours(candidates.getFirst(), start, end), start, end, risks));
            }
            return safe.map(Scored::path);
        } catch (WalkingRouteUnavailableException e) {
            log.warn("걷기 경로 서버를 쓸 수 없어 도로 정보 없이 찾습니다: {}", e.getMessage());
            return gridPlanner.plan(start, end, mode, safetyRecords);
        }
    }

    private Optional<Scored> safest(List<Scored> candidates) {
        return candidates.stream()
                .filter(candidate -> !candidate.passesDanger())
                .min(Comparator.comparingDouble(Scored::safeCost));
    }

    private Scored best(List<Scored> candidates, PathMode mode) {
        Comparator<Scored> order = mode == PathMode.SHORTEST
                ? Comparator.comparingDouble(candidate -> candidate.path().distanceMeters())
                : Comparator.comparingDouble(Scored::safeCost);
        return candidates.stream().min(order).orElseThrow();
    }

    // 첫 위험 지점 양옆을 거쳐 가는 경로들. 서로 상관없으므로 한꺼번에 묻는다.
    private List<List<GeoPoint>> detours(Scored blocked, GeoPoint start, GeoPoint end) {
        List<GeoPoint> points = blocked.path().points();
        int index = blocked.firstDangerIndex();
        GeoPoint danger = points.get(index);
        GeoPoint before = points.get(Math.max(0, index - 1));
        GeoPoint after = points.get(Math.min(points.size() - 1, index + 1));
        double metersPerLongitudeDegree = METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(danger.latitude()));
        double east = (after.longitude() - before.longitude()) * metersPerLongitudeDegree;
        double north = (after.latitude() - before.latitude()) * METERS_PER_LATITUDE_DEGREE;
        double length = Math.hypot(east, north);
        if (length == 0) {
            return List.of();
        }
        List<GeoPoint> vias = new ArrayList<>();
        for (double offset : DETOUR_OFFSETS_METERS) {
            // 진행 방향에 수직인 쪽으로 offset만큼 옮긴 곳
            double offsetEast = -north / length * offset;
            double offsetNorth = east / length * offset;
            vias.add(new GeoPoint(
                    danger.latitude() + offsetNorth / METERS_PER_LATITUDE_DEGREE,
                    danger.longitude() + offsetEast / metersPerLongitudeDegree
            ));
        }
        List<List<GeoPoint>> routes = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<List<GeoPoint>>>> futures = vias.stream()
                    .map(via -> executor.submit(() -> walkingRoutes.routes(List.of(start, via, end), false)))
                    .toList();
            for (Future<List<List<GeoPoint>>> future : futures) {
                try {
                    routes.addAll(future.get());
                } catch (Exception e) {
                    // 우회 후보 하나를 못 받아도 나머지로 고른다.
                    log.debug("우회 경로 후보를 받지 못했습니다: {}", e.toString());
                }
            }
        }
        return routes;
    }

    private List<Scored> scoreAll(List<List<GeoPoint>> routes, GeoPoint start, GeoPoint end, RiskCells risks) {
        return routes.stream().map(route -> score(route, start, end, risks)).toList();
    }

    // 경로를 5m 간격으로 잘라 위험도를 매긴다. 처음과 끝에는 서버가 붙인 도로 위 지점 대신 실제 출발지·도착지를 잇는다.
    private Scored score(List<GeoPoint> route, GeoPoint start, GeoPoint end, RiskCells risks) {
        List<GeoPoint> points = new ArrayList<>();
        points.add(start);
        points.addAll(route);
        points.add(end);

        double distance = 0;
        double weightedRisk = 0;
        double safeCost = 0;
        double uncollected = 0;
        double maxRisk = 0;
        int firstDangerIndex = -1;
        for (int i = 1; i < points.size(); i++) {
            GeoPoint from = points.get(i - 1);
            GeoPoint to = points.get(i);
            double length = from.distanceTo(to);
            if (length == 0) {
                continue;
            }
            int samples = (int) Math.max(1, Math.ceil(length / SAMPLE_METERS));
            double piece = length / samples;
            for (int s = 0; s < samples; s++) {
                double ratio = (s + 0.5) / samples;
                GeoPoint sample = new GeoPoint(
                        from.latitude() + (to.latitude() - from.latitude()) * ratio,
                        from.longitude() + (to.longitude() - from.longitude()) * ratio
                );
                OptionalDouble known = risks.riskAt(sample);
                double risk = known.orElse(AStarPathPlanner.UNKNOWN_RISK_SCORE);
                if (known.isEmpty()) {
                    uncollected += piece;
                }
                weightedRisk += risk * piece;
                safeCost += piece * Math.max(MIN_RISK_SCORE, risk);
                maxRisk = Math.max(maxRisk, risk);
                boolean nearEndpoint = sample.distanceTo(start) <= ENDPOINT_EXEMPT_METERS
                        || sample.distanceTo(end) <= ENDPOINT_EXEMPT_METERS;
                if (firstDangerIndex < 0 && !nearEndpoint && SafetyLevel.fromRiskScore(risk) == SafetyLevel.DANGER) {
                    firstDangerIndex = i;
                }
            }
            distance += length;
        }
        double averageRisk = distance == 0 ? AStarPathPlanner.UNKNOWN_RISK_SCORE : weightedRisk / distance;
        PlannedPath path = new PlannedPath(
                List.copyOf(points),
                roundToTenth(distance),
                roundToTenth(averageRisk),
                SafetyLevel.fromRiskScore(maxRisk == 0 ? averageRisk : maxRisk),
                roundToTenth(uncollected),
                true
        );
        return new Scored(path, safeCost, firstDangerIndex);
    }

    private static double roundToTenth(double value) {
        return Math.round(value * 10) / 10.0;
    }

    // firstDangerIndex: 위험 칸을 처음 지나는 구간이 끝나는 점의 번호. 없으면 -1
    private record Scored(PlannedPath path, double safeCost, int firstDangerIndex) {
        boolean passesDanger() {
            return firstDangerIndex >= 0;
        }
    }
}
