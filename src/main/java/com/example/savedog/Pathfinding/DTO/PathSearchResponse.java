package com.example.savedog.Pathfinding.DTO;

import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.planner.PlannedPath;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.global.common.GeoPoint;

import java.util.List;

// uncollectedDistanceMeters: 경로 중 로보독이 아직 안 지나가 위험도를 모르는(3점으로 어림한) 구간의 길이(m)
// followsRoads: OpenStreetMap 도로를 따라간 경로인지. false면 도로 정보 없이 찾은 경로라 건물을 가로지를 수 있다.
public record PathSearchResponse(
        PathMode mode,
        List<GeoPoint> path,
        double distanceMeters,
        double averageRiskScore,
        SafetyLevel maxRiskLevel,
        double uncollectedDistanceMeters,
        boolean followsRoads
) {
    public PathSearchResponse(
            PathMode mode, List<GeoPoint> path, double distanceMeters, double averageRiskScore, SafetyLevel maxRiskLevel
    ) {
        this(mode, path, distanceMeters, averageRiskScore, maxRiskLevel, 0, false);
    }

    public static PathSearchResponse of(PathMode mode, PlannedPath plannedPath) {
        return new PathSearchResponse(
                mode,
                plannedPath.points(),
                plannedPath.distanceMeters(),
                plannedPath.averageRiskScore(),
                plannedPath.maxRiskLevel(),
                plannedPath.uncollectedMeters(),
                plannedPath.followsRoads()
        );
    }
}
