package com.example.savedog.Pathfinding.DTO;

// 요청으로 받는 좌표. 빠진 값을 0으로 착각하지 않도록 null을 허용하는 타입으로 받아 검사한다.
public record CoordinateRequest(
        Double latitude,
        Double longitude
) {
}
