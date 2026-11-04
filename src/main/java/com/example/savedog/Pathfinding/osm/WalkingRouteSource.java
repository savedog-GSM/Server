package com.example.savedog.Pathfinding.osm;

import com.example.savedog.global.common.GeoPoint;

import java.util.List;

/**
 * 도로를 따라가는 걷기 경로 후보를 준다. (OpenStreetMap 데이터)
 * 경로마다 출발→도착 순서의 좌표 목록이다. 갈 수 있는 길이 없으면 빈 목록.
 * 서비스에 닿지 못하면(꺼짐, 시간 초과, 사용량 초과 등) WalkingRouteUnavailableException을 던진다.
 */
public interface WalkingRouteSource {
    // waypoints: 출발지, (거쳐 갈 곳), 도착지. alternatives: 다른 후보 경로도 받을지 (거쳐 갈 곳이 있으면 무시될 수 있다)
    List<List<GeoPoint>> routes(List<GeoPoint> waypoints, boolean alternatives);

    default boolean available() {
        return true;
    }

    class WalkingRouteUnavailableException extends RuntimeException {
        public WalkingRouteUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
