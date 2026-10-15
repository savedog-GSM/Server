package com.example.savedog.Safety.analyzer;

import com.example.savedog.global.common.GeoPoint;

// 위험도 판단에 쓰는 입력 한 묶음. location은 최근 위치가 없으면 null.
// distance, 그리고 roll·pitch 한 쌍은 그 값을 보내는 장치가 조용하면 null (roll과 pitch는 함께 있거나 함께 없다)
public record SafetyContext(
        GeoPoint location,
        Double distance,
        Double roll,
        Double pitch
) {
}
