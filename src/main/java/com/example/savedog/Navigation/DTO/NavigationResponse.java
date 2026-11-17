package com.example.savedog.Navigation.DTO;

import com.example.savedog.Navigation.domain.NavigationPurpose;
import com.example.savedog.Navigation.domain.NavigationStatus;
import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Robot.domain.CommandType;
import com.example.savedog.global.common.GeoPoint;

import java.time.LocalDateTime;

/**
 * 로보독 자동 이동 상태.
 * purpose: 목적지 안내(DESTINATION)인지 주인에게 돌아가기(OWNER)인지. 아무것도 안 했으면 null
 * target: 가는 곳, robot: 로보독(에 달린 폰)의 마지막 위치, distanceMeters: 둘 사이 직선거리 (모르면 null)
 * plannedPath: 지금 따라가는 경로와 그 길이·위험도. 아직 못 찾았으면 null (30초마다, 경로를 벗어나면 다시 찾는다)
 * lastCommand: 마지막으로 로보독에 보낸 명령
 */
public record NavigationResponse(
        boolean active,
        NavigationPurpose purpose,
        PathMode mode,
        NavigationStatus status,
        String message,
        GeoPoint target,
        Double targetAccuracy,
        LocalDateTime targetUpdatedAt,
        GeoPoint robot,
        Double distanceMeters,
        PathSearchResponse plannedPath,
        CommandType lastCommand,
        LocalDateTime lastCommandAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
