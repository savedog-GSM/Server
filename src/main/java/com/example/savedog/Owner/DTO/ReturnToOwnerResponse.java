package com.example.savedog.Owner.DTO;

import com.example.savedog.Owner.service.ReturnStatus;
import com.example.savedog.Robot.domain.CommandType;
import com.example.savedog.global.common.GeoPoint;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주인에게 돌아가기 상태.
 * owner: 주인 폰이 마지막으로 보낸 위치, robot: 로보독(에 달린 폰)의 마지막 위치
 * distanceMeters: 둘 사이 직선거리. 둘 중 하나라도 모르면 null
 * route: 지금 따라가는 경로(지도에 그릴 수 있다), lastCommand: 마지막으로 로보독에 보낸 명령
 */
public record ReturnToOwnerResponse(
        boolean active,
        ReturnStatus status,
        String message,
        GeoPoint owner,
        Double ownerAccuracy,
        LocalDateTime ownerUpdatedAt,
        GeoPoint robot,
        Double distanceMeters,
        List<GeoPoint> route,
        CommandType lastCommand,
        LocalDateTime lastCommandAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
