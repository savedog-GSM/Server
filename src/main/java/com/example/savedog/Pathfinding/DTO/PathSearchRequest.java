package com.example.savedog.Pathfinding.DTO;

import com.example.savedog.Pathfinding.domain.PathMode;

// mode를 생략하면 SAFEST
public record PathSearchRequest(
        CoordinateRequest start,
        CoordinateRequest end,
        PathMode mode
) {
}
