package com.example.savedog.Pathfinding.DTO;

public record RobotTelemetry(
        Long robotId,
        double latitude,
        double longitude,
        double speed
) {
}
