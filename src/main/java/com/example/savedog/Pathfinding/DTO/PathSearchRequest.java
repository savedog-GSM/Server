package com.example.savedog.Pathfinding.DTO;

import com.example.savedog.global.common.GeoPoint;

public record PathSearchRequest(
        GeoPoint start,
        GeoPoint end
) {
}
