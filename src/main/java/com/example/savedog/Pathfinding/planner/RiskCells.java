package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.global.common.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

// 위험도 기록을 10m 칸으로 묶어, 어떤 지점이 속한 칸의 위험도를 알려 준다. 기록이 없는 칸은 비어 있다.
final class RiskCells {
    private static final double CELL_SIZE_METERS = 10;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;

    private final double metersPerLongitudeDegree;
    private final Map<Long, Double> riskByCell = new HashMap<>();

    // referenceLatitude: 경도 1도의 길이를 정할 기준 위도 (경로 근처면 된다)
    RiskCells(List<SafetyRecord> records, double referenceLatitude) {
        this.metersPerLongitudeDegree = METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(referenceLatitude));
        Map<Long, List<SafetyRecord>> byCell = new HashMap<>();
        for (SafetyRecord record : records) {
            byCell.computeIfAbsent(keyOf(record.getLatitude(), record.getLongitude()), key -> new ArrayList<>()).add(record);
        }
        byCell.forEach((key, cellRecords) -> riskByCell.put(key, RecencyRisk.of(cellRecords)));
    }

    OptionalDouble riskAt(GeoPoint point) {
        Double risk = riskByCell.get(keyOf(point.latitude(), point.longitude()));
        return risk == null ? OptionalDouble.empty() : OptionalDouble.of(risk);
    }

    private long keyOf(double latitude, double longitude) {
        long row = (long) Math.floor(latitude * METERS_PER_LATITUDE_DEGREE / CELL_SIZE_METERS);
        long column = (long) Math.floor(longitude * metersPerLongitudeDegree / CELL_SIZE_METERS);
        return row * 100_000_000L + column;
    }
}
