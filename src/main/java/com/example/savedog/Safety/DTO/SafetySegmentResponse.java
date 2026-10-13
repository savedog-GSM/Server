package com.example.savedog.Safety.DTO;

import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.global.common.GeoPoint;

import java.util.List;

// 같은 등급이 이어지는 구간 하나. 지도에서 Polyline 한 줄에 해당
public record SafetySegmentResponse(
        SafetyLevel level,
        List<GeoPoint> path
) {
}
