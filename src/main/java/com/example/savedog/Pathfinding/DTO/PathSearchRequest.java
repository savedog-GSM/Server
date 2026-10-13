package com.example.savedog.Pathfinding.DTO;

import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.global.common.GeoPoint;

// mode를 생략하면 SAFEST
public record PathSearchRequest(
        GeoPoint start,
        GeoPoint end,
        PathMode mode
) {
}
