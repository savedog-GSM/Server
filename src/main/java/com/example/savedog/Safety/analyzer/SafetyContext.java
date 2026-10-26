package com.example.savedog.Safety.analyzer;

import com.example.savedog.Vision.service.VisionFinding;
import com.example.savedog.global.common.GeoPoint;

// 위험도 판단에 쓰는 입력 한 묶음. location은 최근 위치가 없으면 null, distance는 전방 거리(cm)
// vision은 최근 3초 안에 받은 카메라 사진의 판단. 모델이 없거나 최근 사진이 없으면 null
public record SafetyContext(
        GeoPoint location,
        double distance,
        VisionFinding vision
) {
    public SafetyContext(GeoPoint location, double distance) {
        this(location, distance, null);
    }
}
