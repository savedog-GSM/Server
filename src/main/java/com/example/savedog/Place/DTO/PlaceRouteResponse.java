package com.example.savedog.Place.DTO;

import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.global.common.GeoPoint;

/**
 * 가까운 편의 시설까지의 자동 길찾기 결과.
 * start: 경로를 찾은 출발지(요청 좌표, 없으면 스마트폰의 최근 위치)
 * place: 고른 장소, path: 그 장소까지의 경로
 * checkedPlaces: 경로를 시험해 본 후보 수, reachablePlaces: 그중 경로가 있었던 수
 */
public record PlaceRouteResponse(
        String type,
        GeoPoint start,
        PlaceResponse place,
        PathSearchResponse path,
        int checkedPlaces,
        int reachablePlaces
) {
}
