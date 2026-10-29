package com.example.savedog.Telemetry.DTO;

// distance: 아두이노가 잰 전방 거리(cm)
// temperature(°C)·humidity(%): 아두이노 온습도 센서 값. 센서가 없거나 못 읽었으면 null
public record SensorValues(
        Double distance,
        Double temperature,
        Double humidity
) {
    public SensorValues(Double distance) {
        this(distance, null, null);
    }
}
