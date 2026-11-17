package com.example.savedog.Navigation.DTO;

import com.example.savedog.Pathfinding.domain.PathMode;

// 목적지 좌표. mode를 빼면 안전 우선(SAFEST)
public record NavigationRequest(
        Double latitude,
        Double longitude,
        PathMode mode
) {
}
