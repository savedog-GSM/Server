package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.global.common.GeoPoint;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 위험도 기록을 격자 칸으로 묶어 그래프를 만들고 A*로 경로를 찾는다. (기능명세서 F-09, F-10)
 * 칸 = 지점(노드), 로보독이 연달아 지나간 칸 사이 = 길(간선).
 */
@Component
public class AStarPathPlanner implements PathPlanner {
    // 스마트폰 GPS 오차(5~10m)보다 잘게 나누면 같은 길이 여러 칸으로 흩어진다.
    private static final double CELL_SIZE_METERS = 10;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;
    // 출발지·도착지를 붙일 칸이 이 거리 안에 없으면 경로 없음
    private static final double MAX_SNAP_DISTANCE_METERS = 30;
    // 연달아 찍힌 두 기록의 간격이 이보다 길면 이어서 지나간 것으로 보지 않는다.
    private static final Duration MAX_LINK_GAP = Duration.ofSeconds(10);
    // 같은 칸의 기록이 이보다 길게 비면 다른 회차(다시 지나감)로 본다.
    private static final Duration PASS_GAP = Duration.ofSeconds(60);
    private static final double MIN_RISK_SCORE = 1;

    @Override
    public Optional<PlannedPath> plan(GeoPoint start, GeoPoint end, PathMode mode, List<SafetyRecord> safetyRecords) {
        Collection<Cell> cells = buildGraph(safetyRecords);
        Optional<Cell> startCell = nearestCell(cells, start, mode);
        Optional<Cell> endCell = nearestCell(cells, end, mode);
        if (startCell.isEmpty() || endCell.isEmpty()) {
            return Optional.empty();
        }
        return search(startCell.get(), endCell.get(), mode).map(this::toPlannedPath);
    }

    private Collection<Cell> buildGraph(List<SafetyRecord> records) {
        Map<CellKey, Cell> cells = new HashMap<>();
        List<SafetyRecord> track = records.stream()
                .sorted(Comparator.comparing(SafetyRecord::getAssessedAt))
                .toList();

        SafetyRecord previous = null;
        Cell previousCell = null;
        for (SafetyRecord record : track) {
            Cell cell = cells.computeIfAbsent(CellKey.of(record.getLatitude(), record.getLongitude()), key -> new Cell());
            cell.records.add(record);
            if (previousCell != null && previousCell != cell && isContinuous(previous, record)) {
                previousCell.neighbors.add(cell);
                cell.neighbors.add(previousCell);
            }
            previous = record;
            previousCell = cell;
        }

        cells.values().forEach(Cell::summarize);
        return cells.values();
    }

    private boolean isContinuous(SafetyRecord previous, SafetyRecord current) {
        return Duration.between(previous.getAssessedAt(), current.getAssessedAt()).compareTo(MAX_LINK_GAP) <= 0;
    }

    private Optional<Cell> nearestCell(Collection<Cell> cells, GeoPoint point, PathMode mode) {
        return cells.stream()
                .filter(cell -> isPassable(cell, mode))
                .filter(cell -> cell.center.distanceTo(point) <= MAX_SNAP_DISTANCE_METERS)
                .min(Comparator.comparingDouble(cell -> cell.center.distanceTo(point)));
    }

    private Optional<List<Cell>> search(Cell start, Cell goal, PathMode mode) {
        Map<Cell, Double> bestCost = new HashMap<>();
        Map<Cell, Cell> cameFrom = new HashMap<>();
        PriorityQueue<Candidate> open = new PriorityQueue<>(Comparator.comparingDouble(Candidate::estimatedTotal));
        bestCost.put(start, 0.0);
        open.add(new Candidate(start, 0, start.center.distanceTo(goal.center)));

        while (!open.isEmpty()) {
            Candidate current = open.poll();
            if (current.cell == goal) {
                return Optional.of(tracePath(cameFrom, goal));
            }
            // 더 싼 경로가 이미 발견된 칸의 옛 후보는 건너뛴다.
            if (current.cost > bestCost.get(current.cell)) {
                continue;
            }
            for (Cell next : current.cell.neighbors) {
                if (!isPassable(next, mode)) {
                    continue;
                }
                double cost = current.cost + edgeCost(current.cell, next, mode);
                if (cost < bestCost.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    bestCost.put(next, cost);
                    cameFrom.put(next, current.cell);
                    // 남은 거리를 직선거리로 어림한다. 실제 비용은 항상 이보다 크거나 같아서 최적 경로가 보장된다.
                    open.add(new Candidate(next, cost, cost + next.center.distanceTo(goal.center)));
                }
            }
        }
        return Optional.empty();
    }

    private boolean isPassable(Cell cell, PathMode mode) {
        return mode == PathMode.SHORTEST || cell.level != SafetyLevel.DANGER;
    }

    private double edgeCost(Cell from, Cell to, PathMode mode) {
        double length = from.center.distanceTo(to.center);
        if (mode == PathMode.SHORTEST) {
            return length;
        }
        // 위험도 점수(1~5)를 그대로 곱한다. 1점인 길은 길이 그대로, 5점인 길은 5배.
        double risk = (from.riskScore + to.riskScore) / 2;
        return length * Math.max(MIN_RISK_SCORE, risk);
    }

    private List<Cell> tracePath(Map<Cell, Cell> cameFrom, Cell goal) {
        List<Cell> path = new ArrayList<>();
        for (Cell cell = goal; cell != null; cell = cameFrom.get(cell)) {
            path.add(cell);
        }
        Collections.reverse(path);
        return path;
    }

    private PlannedPath toPlannedPath(List<Cell> path) {
        double distance = 0;
        for (int i = 1; i < path.size(); i++) {
            distance += path.get(i - 1).center.distanceTo(path.get(i).center);
        }
        double averageRisk = path.stream().mapToDouble(cell -> cell.riskScore).average().orElse(0);
        SafetyLevel maxLevel = path.stream()
                .map(cell -> cell.level)
                .max(Comparator.naturalOrder())
                .orElse(SafetyLevel.SAFE);
        return new PlannedPath(
                path.stream().map(cell -> cell.center).toList(),
                roundToTenth(distance),
                roundToTenth(averageRisk),
                maxLevel
        );
    }

    private static double roundToTenth(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private record Candidate(Cell cell, double cost, double estimatedTotal) {
    }

    private record CellKey(long row, long column) {
        static CellKey of(double latitude, double longitude) {
            double latitudeStep = CELL_SIZE_METERS / METERS_PER_LATITUDE_DEGREE;
            long row = (long) Math.floor(latitude / latitudeStep);
            // 경도 1도의 길이는 위도에 따라 줄어들므로 칸이 속한 줄의 위도로 보정한다.
            double rowLatitude = (row + 0.5) * latitudeStep;
            double longitudeStep = latitudeStep / Math.cos(Math.toRadians(rowLatitude));
            long column = (long) Math.floor(longitude / longitudeStep);
            return new CellKey(row, column);
        }
    }

    private static final class Cell {
        private final List<SafetyRecord> records = new ArrayList<>();
        private final Set<Cell> neighbors = new LinkedHashSet<>();
        private GeoPoint center;
        private double riskScore;
        private SafetyLevel level;

        // 기록을 다 모은 뒤 대표 위치(기록들의 평균 좌표)와 위험도를 정한다.
        private void summarize() {
            center = new GeoPoint(
                    records.stream().mapToDouble(SafetyRecord::getLatitude).average().orElseThrow(),
                    records.stream().mapToDouble(SafetyRecord::getLongitude).average().orElseThrow()
            );
            riskScore = recencyWeightedRisk();
            level = SafetyLevel.fromRiskScore(riskScore);
        }

        // 지나간 회차별 평균을 구하고, 최근 회차일수록 2배씩 무겁게 쳐서 평균한다.
        private double recencyWeightedRisk() {
            List<Double> passAverages = new ArrayList<>();
            double sum = 0;
            int count = 0;
            LocalDateTime previousAt = null;
            for (SafetyRecord record : records) {
                boolean newPass = previousAt != null
                        && Duration.between(previousAt, record.getAssessedAt()).compareTo(PASS_GAP) > 0;
                if (newPass) {
                    passAverages.add(sum / count);
                    sum = 0;
                    count = 0;
                }
                sum += record.getRiskScore();
                count++;
                previousAt = record.getAssessedAt();
            }
            passAverages.add(sum / count);

            double weightedSum = 0;
            double totalWeight = 0;
            double weight = 1;
            for (int i = passAverages.size() - 1; i >= 0; i--) {
                weightedSum += passAverages.get(i) * weight;
                totalWeight += weight;
                weight /= 2;
            }
            return weightedSum / totalWeight;
        }
    }
}
