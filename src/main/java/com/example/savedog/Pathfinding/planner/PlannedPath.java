package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.global.common.GeoPoint;

import java.util.List;

// points: 출발→도착 순서, averageRiskScore: 경로가 지나는 지점들의 평균 위험도(1~5), maxRiskLevel: 그중 가장 높은 등급
// uncollectedMeters: 그중 로보독이 아직 안 지나가서 위험도를 3점으로 어림한 구간의 길이
// followsRoads: OpenStreetMap 도로를 따라간 경로인지 (false면 도로 정보 없이 칸으로 찾은 경로)
public record PlannedPath(
        List<GeoPoint> points,
        double distanceMeters,
        double averageRiskScore,
        SafetyLevel maxRiskLevel,
        double uncollectedMeters,
        boolean followsRoads
) {
}
