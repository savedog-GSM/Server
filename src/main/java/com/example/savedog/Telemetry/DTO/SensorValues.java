package com.example.savedog.Telemetry.DTO;

// distance: 전방 초음파 거리(cm), roll: 좌우 기울기(°), pitch: 앞뒤 기울기(°)
public record SensorValues(
        Double distance,
        Double roll,
        Double pitch
) {
}
