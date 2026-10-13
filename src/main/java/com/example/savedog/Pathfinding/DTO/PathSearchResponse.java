package com.example.savedog.Pathfinding.DTO;

import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.planner.PlannedPath;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.global.common.GeoPoint;

import java.util.List;

public record PathSearchResponse(
        PathMode mode,
        List<GeoPoint> path,
        double distanceMeters,
        double averageRiskScore,
        SafetyLevel maxRiskLevel
) {
    public static PathSearchResponse of(PathMode mode, PlannedPath plannedPath) {
        return new PathSearchResponse(
                mode,
                plannedPath.points(),
                plannedPath.distanceMeters(),
                plannedPath.averageRiskScore(),
                plannedPath.maxRiskLevel()
        );
    }
}
