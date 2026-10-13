package com.example.savedog.Pathfinding.DTO;

import com.example.savedog.Pathfinding.planner.PlannedPath;
import com.example.savedog.global.common.GeoPoint;

import java.util.List;

public record PathSearchResponse(
        List<GeoPoint> path,
        double distanceMeters,
        double averageSafetyScore
) {
    public static PathSearchResponse from(PlannedPath plannedPath) {
        return new PathSearchResponse(
                plannedPath.points(),
                plannedPath.distanceMeters(),
                plannedPath.averageSafetyScore()
        );
    }
}
