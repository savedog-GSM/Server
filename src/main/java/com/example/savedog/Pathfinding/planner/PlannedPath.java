package com.example.savedog.Pathfinding.planner;

import com.example.savedog.global.common.GeoPoint;

import java.util.List;

// points: 출발→도착 순서, averageSafetyScore: 경로가 지나는 지점들의 평균 안전 점수(0~100)
public record PlannedPath(
        List<GeoPoint> points,
        double distanceMeters,
        double averageSafetyScore
) {
}
