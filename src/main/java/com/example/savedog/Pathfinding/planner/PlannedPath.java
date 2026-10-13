package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.global.common.GeoPoint;

import java.util.List;

// points: 출발→도착 순서, averageRiskScore: 경로가 지나는 지점들의 평균 위험도(1~5), maxRiskLevel: 그중 가장 높은 등급
public record PlannedPath(
        List<GeoPoint> points,
        double distanceMeters,
        double averageRiskScore,
        SafetyLevel maxRiskLevel
) {
}
