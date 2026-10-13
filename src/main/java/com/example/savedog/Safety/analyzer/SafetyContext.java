package com.example.savedog.Safety.analyzer;

import com.example.savedog.global.common.GeoPoint;

// 위험도 판단에 쓰는 입력 한 묶음. location은 최근 위치가 없으면 null
public record SafetyContext(
        GeoPoint location,
        double distance,
        double roll,
        double pitch
) {
}
