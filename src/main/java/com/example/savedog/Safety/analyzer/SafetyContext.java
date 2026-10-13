package com.example.savedog.Safety.analyzer;

import com.example.savedog.Telemetry.domain.SensorReading;
import com.example.savedog.global.common.GeoPoint;

import java.util.List;

// 안전도 판단에 쓰는 입력 한 묶음. cameraFrame(JPEG)은 최근 프레임이 없으면 null
public record SafetyContext(
        Long robotId,
        GeoPoint location,
        double speed,
        List<SensorReading> sensorReadings,
        byte[] cameraFrame
) {
}
