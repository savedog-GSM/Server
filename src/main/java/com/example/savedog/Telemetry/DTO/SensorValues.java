package com.example.savedog.Telemetry.DTO;

// distance: 전방 거리(cm, 아두이노), roll: 좌우 기울기(°), pitch: 앞뒤 기울기(°, 둘 다 로보독)
// 장치마다 자기가 재는 값만 보내므로 요청에서도 응답에서도 일부가 null일 수 있다.
public record SensorValues(
        Double distance,
        Double roll,
        Double pitch
) {
    public boolean hasDistance() {
        return distance != null;
    }

    public boolean hasTilt() {
        return roll != null && pitch != null;
    }
}
