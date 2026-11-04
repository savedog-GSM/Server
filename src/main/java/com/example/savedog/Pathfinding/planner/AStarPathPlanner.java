package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.global.common.GeoPoint;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * 출발지·도착지를 감싸는 지역을 10m 칸으로 나누고 A*로 경로를 찾는다. (기능명세서 F-09, F-10)
 * - 로보독이 지나간 칸: 쌓인 위험도 기록의 점수(1~5)를 쓴다.
 * - 아직 아무도 안 지나간 칸: 위험도를 모르므로 3점(주의)으로 본다.
 * 그래서 기록이 하나도 없어도 경로가 나오고, 기록이 쌓일수록 안전하다고 확인된 길(1~2점)을 더 고른다.
 * 칸 사이는 상하좌우·대각선으로 이어진다. 지도(건물·도로) 정보는 쓰지 않으므로 기록 없는 구간은 직선에 가깝다.
 */
@Component
public class AStarPathPlanner implements PathPlanner {
    // 스마트폰 GPS 오차(5~10m)보다 잘게 나누면 같은 길이 여러 칸으로 흩어진다.
    private static final double CELL_SIZE_METERS = 10;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;
    // 기록이 없는 칸의 위험도
    static final double UNKNOWN_RISK_SCORE = 3;
    private static final double MIN_RISK_SCORE = 1;
    // 출발지·도착지를 감싼 사각형 바깥으로 돌아갈 수 있는 여유. 직선거리에 비례하되 이 범위 안에서 정한다.
    private static final double MIN_MARGIN_METERS = 100;
    private static final double MAX_MARGIN_METERS = 500;
    private static final double MARGIN_RATIO = 0.3;
    // 칸이 이보다 많아지는 먼 거리는 찾지 않는다. (약 10km × 10km)
    private static final int MAX_CELLS = 1_000_000;
    private static final int[][] STEPS = {
            {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };

    @Override
    public Optional<PlannedPath> plan(GeoPoint start, GeoPoint end, PathMode mode, List<SafetyRecord> safetyRecords) {
        Grid grid = Grid.around(start, end);
        if (grid == null) {
            return Optional.empty();
        }
        grid.applyRecords(safetyRecords);
        int startCell = grid.cellOf(start);
        int endCell = grid.cellOf(end);
        return search(grid, startCell, endCell, mode).map(cells -> toPlannedPath(grid, cells, start, end));
    }

    private Optional<List<Integer>> search(Grid grid, int start, int goal, PathMode mode) {
        double[] bestCost = new double[grid.size()];
        int[] cameFrom = new int[grid.size()];
        Arrays.fill(bestCost, Double.POSITIVE_INFINITY);
        Arrays.fill(cameFrom, -1);
        PriorityQueue<Candidate> open = new PriorityQueue<>(Comparator.comparingDouble(Candidate::estimatedTotal));
        bestCost[start] = 0;
        open.add(new Candidate(start, 0, grid.distance(start, goal)));

        while (!open.isEmpty()) {
            Candidate current = open.poll();
            if (current.cell == goal) {
                return Optional.of(tracePath(cameFrom, goal));
            }
            // 더 싼 경로가 이미 발견된 칸의 옛 후보는 건너뛴다.
            if (current.cost > bestCost[current.cell]) {
                continue;
            }
            int row = grid.rowOf(current.cell);
            int column = grid.columnOf(current.cell);
            for (int[] step : STEPS) {
                int next = grid.cellAt(row + step[0], column + step[1]);
                // 출발 칸과 도착 칸은 위험 등급이어도 이미 있거나 가야 하는 곳이므로 막지 않는다.
                if (next < 0 || (next != goal && !isPassable(grid, next, mode))) {
                    continue;
                }
                double cost = current.cost + edgeCost(grid, current.cell, next, mode);
                if (cost < bestCost[next]) {
                    bestCost[next] = cost;
                    cameFrom[next] = current.cell;
                    // 남은 거리를 직선거리로 어림한다. 실제 비용은 항상 이보다 크거나 같아서 최적 경로가 보장된다.
                    open.add(new Candidate(next, cost, cost + grid.distance(next, goal)));
                }
            }
        }
        return Optional.empty();
    }

    private boolean isPassable(Grid grid, int cell, PathMode mode) {
        return mode == PathMode.SHORTEST || SafetyLevel.fromRiskScore(grid.risk[cell]) != SafetyLevel.DANGER;
    }

    private double edgeCost(Grid grid, int from, int to, PathMode mode) {
        double length = grid.distance(from, to);
        if (mode == PathMode.SHORTEST) {
            return length;
        }
        // 위험도 점수(1~5)를 그대로 곱한다. 1점인 길은 길이 그대로, 모르는 길(3점)은 3배, 5점인 길은 5배.
        double risk = (grid.risk[from] + grid.risk[to]) / 2;
        return length * Math.max(MIN_RISK_SCORE, risk);
    }

    private List<Integer> tracePath(int[] cameFrom, int goal) {
        List<Integer> path = new ArrayList<>();
        for (int cell = goal; cell >= 0; cell = cameFrom[cell]) {
            path.add(cell);
        }
        Collections.reverse(path);
        return path;
    }

    private PlannedPath toPlannedPath(Grid grid, List<Integer> cells, GeoPoint start, GeoPoint end) {
        double uncollected = 0;
        for (int i = 1; i < cells.size(); i++) {
            int from = cells.get(i - 1);
            int to = cells.get(i);
            int unknownEnds = (grid.known[from] ? 0 : 1) + (grid.known[to] ? 0 : 1);
            uncollected += grid.distance(from, to) * unknownEnds / 2;
        }
        double averageRisk = cells.stream().mapToDouble(cell -> grid.risk[cell]).average().orElse(UNKNOWN_RISK_SCORE);
        double maxRisk = cells.stream().mapToDouble(cell -> grid.risk[cell]).max().orElse(UNKNOWN_RISK_SCORE);

        List<GeoPoint> points = simplify(grid, cells, start, end);
        double distance = 0;
        for (int i = 1; i < points.size(); i++) {
            distance += points.get(i - 1).distanceTo(points.get(i));
        }
        return new PlannedPath(
                points,
                roundToTenth(distance),
                roundToTenth(averageRisk),
                SafetyLevel.fromRiskScore(maxRisk),
                roundToTenth(uncollected),
                false
        );
    }

    // 같은 방향으로 이어진 칸은 꺾이는 곳만 남긴다. 처음과 끝은 칸 가운데 대신 실제 출발지·도착지를 쓴다.
    private List<GeoPoint> simplify(Grid grid, List<Integer> cells, GeoPoint start, GeoPoint end) {
        List<GeoPoint> points = new ArrayList<>();
        points.add(start);
        for (int i = 1; i < cells.size() - 1; i++) {
            int previous = cells.get(i - 1);
            int current = cells.get(i);
            int next = cells.get(i + 1);
            boolean straight = grid.rowOf(current) - grid.rowOf(previous) == grid.rowOf(next) - grid.rowOf(current)
                    && grid.columnOf(current) - grid.columnOf(previous) == grid.columnOf(next) - grid.columnOf(current);
            if (!straight) {
                points.add(grid.centerOf(current));
            }
        }
        points.add(end);
        return points;
    }

    private static double roundToTenth(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private record Candidate(int cell, double cost, double estimatedTotal) {
    }

    /**
     * 출발지 주변을 평면으로 펴서 동쪽(x)·북쪽(y) m로 나타낸 칸 묶음. 몇 km 안에서는 오차가 무시할 만하다.
     * 칸 번호 = 줄 × 열 수 + 열
     */
    private static final class Grid {
        private final double originLatitude;
        private final double originLongitude;
        private final double metersPerLongitudeDegree;
        private final int rows;
        private final int columns;
        private final double[] risk;
        private final boolean[] known;

        private Grid(double originLatitude, double originLongitude, double metersPerLongitudeDegree, int rows, int columns) {
            this.originLatitude = originLatitude;
            this.originLongitude = originLongitude;
            this.metersPerLongitudeDegree = metersPerLongitudeDegree;
            this.rows = rows;
            this.columns = columns;
            this.risk = new double[rows * columns];
            this.known = new boolean[rows * columns];
            Arrays.fill(risk, UNKNOWN_RISK_SCORE);
        }

        // 너무 넓으면 null
        static Grid around(GeoPoint start, GeoPoint end) {
            double margin = Math.clamp(start.distanceTo(end) * MARGIN_RATIO, MIN_MARGIN_METERS, MAX_MARGIN_METERS);
            double metersPerLongitudeDegree = METERS_PER_LATITUDE_DEGREE
                    * Math.cos(Math.toRadians((start.latitude() + end.latitude()) / 2));
            double south = Math.min(start.latitude(), end.latitude()) - margin / METERS_PER_LATITUDE_DEGREE;
            double north = Math.max(start.latitude(), end.latitude()) + margin / METERS_PER_LATITUDE_DEGREE;
            double west = Math.min(start.longitude(), end.longitude()) - margin / metersPerLongitudeDegree;
            double east = Math.max(start.longitude(), end.longitude()) + margin / metersPerLongitudeDegree;
            long rows = (long) Math.ceil((north - south) * METERS_PER_LATITUDE_DEGREE / CELL_SIZE_METERS);
            long columns = (long) Math.ceil((east - west) * metersPerLongitudeDegree / CELL_SIZE_METERS);
            if (rows * columns > MAX_CELLS) {
                return null;
            }
            return new Grid(south, west, metersPerLongitudeDegree, (int) rows, (int) columns);
        }

        int size() {
            return rows * columns;
        }

        int cellOf(GeoPoint point) {
            int row = (int) Math.floor((point.latitude() - originLatitude) * METERS_PER_LATITUDE_DEGREE / CELL_SIZE_METERS);
            int column = (int) Math.floor((point.longitude() - originLongitude) * metersPerLongitudeDegree / CELL_SIZE_METERS);
            return cellAt(row, column);
        }

        // 범위 밖이면 -1
        int cellAt(int row, int column) {
            if (row < 0 || row >= rows || column < 0 || column >= columns) {
                return -1;
            }
            return row * columns + column;
        }

        int rowOf(int cell) {
            return cell / columns;
        }

        int columnOf(int cell) {
            return cell % columns;
        }

        GeoPoint centerOf(int cell) {
            return new GeoPoint(
                    originLatitude + (rowOf(cell) + 0.5) * CELL_SIZE_METERS / METERS_PER_LATITUDE_DEGREE,
                    originLongitude + (columnOf(cell) + 0.5) * CELL_SIZE_METERS / metersPerLongitudeDegree
            );
        }

        // 두 칸 가운데 사이의 거리(m)
        double distance(int from, int to) {
            double rowDelta = rowOf(from) - rowOf(to);
            double columnDelta = columnOf(from) - columnOf(to);
            return Math.hypot(rowDelta, columnDelta) * CELL_SIZE_METERS;
        }

        // 지역 안의 기록을 칸별로 모아 그 칸의 위험도로 쓴다. 지역 밖의 기록은 쓰지 않는다.
        void applyRecords(List<SafetyRecord> records) {
            Map<Integer, List<SafetyRecord>> byCell = new HashMap<>();
            for (SafetyRecord record : records) {
                int cell = cellOf(new GeoPoint(record.getLatitude(), record.getLongitude()));
                if (cell >= 0) {
                    byCell.computeIfAbsent(cell, key -> new ArrayList<>()).add(record);
                }
            }
            byCell.forEach((cell, cellRecords) -> {
                risk[cell] = RecencyRisk.of(cellRecords);
                known[cell] = true;
            });
        }
    }
}
