package com.example.savedog.Place.service;

import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.service.PathfindingService;
import com.example.savedog.Place.DTO.PlaceRouteResponse;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 가까운 화장실·편의점을 찾아 그곳까지의 경로를 자동으로 고른다.
 * 1) 카카오 장소 검색으로 가까운 순서 후보를 몇 곳 받는다.
 * 2) 후보마다 로보독이 다닌 길(위험도 기록) 위에서 A*로 경로를 찾는다. (기본은 안전 우선)
 * 3) 경로가 있는 후보 중 비용이 가장 낮은 곳을 고른다.
 *    안전 우선: 길이 × 평균 위험도(1~5)  /  최단 거리: 길이
 * 직선으로 가장 가까운 곳이라도 위험한 길로만 갈 수 있으면 조금 먼 안전한 곳을 고를 수 있다.
 */
@Service
@RequiredArgsConstructor
public class PlaceRouteService {
    // 경로를 시험해 볼 후보 수. 후보마다 A*를 돌리므로 너무 늘리지 않는다.
    static final int CANDIDATES = 5;
    // 출발지를 주지 않았을 때 쓸 스마트폰 위치의 최대 나이. 멈춰 서서 길을 묻는 경우가 많아 조금 넉넉히 둔다.
    private static final Duration LOCATION_MAX_AGE = Duration.ofSeconds(60);
    private static final double MIN_RISK_SCORE = 1;

    private final NearbyPlaceService nearbyPlaceService;
    private final PathfindingService pathfindingService;
    private final LocationService locationService;

    public PlaceRouteResponse route(String type, Double latitude, Double longitude, Integer radiusMeters, PathMode mode) {
        String normalizedType = NearbyPlaceService.normalizeType(type);
        int radius = NearbyPlaceService.radiusOrDefault(radiusMeters);
        PathMode pathMode = mode == null ? PathMode.SAFEST : mode;
        GeoPoint start = startOf(latitude, longitude);

        List<KakaoPlace> candidates = nearbyPlaceService.findNearest(normalizedType, start, radius, CANDIDATES);
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.PLACE_NOT_FOUND,
                    "반경 %dm 안에서 %s을(를) 찾지 못했습니다.".formatted(radius, label(normalizedType)));
        }

        List<Route> routes = candidates.stream()
                .map(place -> pathfindingService.plan(start, new GeoPoint(place.latitude(), place.longitude()), pathMode)
                        .map(path -> new Route(place, path)))
                .flatMap(Optional::stream)
                .toList();
        Route best = routes.stream()
                .min(Comparator.comparingDouble(route -> cost(route.path(), pathMode)))
                .orElseThrow(() -> new BusinessException(ErrorCode.PATH_NOT_FOUND,
                        "가까운 %s %d곳 모두 로보독이 다닌 길로 이어지지 않습니다. 그 주변 길을 먼저 수집해 주세요."
                                .formatted(label(normalizedType), candidates.size())));

        return new PlaceRouteResponse(
                normalizedType,
                start,
                nearbyPlaceService.toResponse(best.place()),
                best.path(),
                candidates.size(),
                routes.size()
        );
    }

    // 좌표를 주면 그곳에서, 안 주면 스마트폰이 보낸 최근 위치에서 출발한다. 위도·경도는 함께 주어야 한다.
    private GeoPoint startOf(Double latitude, Double longitude) {
        if (latitude != null || longitude != null) {
            return RequestValidator.coordinate(latitude, longitude, "출발지");
        }
        return locationService.findRecent(LOCATION_MAX_AGE)
                .map(location -> new GeoPoint(location.latitude(), location.longitude()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOCATION_UNAVAILABLE,
                        "출발지를 주지 않았는데 최근 1분 안에 받은 정확한(30m 이내) 스마트폰 위치가 없습니다. "
                                + "latitude·longitude를 함께 보내 주세요."));
    }

    private static double cost(PathSearchResponse path, PathMode mode) {
        if (mode == PathMode.SHORTEST) {
            return path.distanceMeters();
        }
        return path.distanceMeters() * Math.max(MIN_RISK_SCORE, path.averageRiskScore());
    }

    private static String label(String normalizedType) {
        return normalizedType.equals("toilet") ? "화장실" : "편의점";
    }

    private record Route(KakaoPlace place, PathSearchResponse path) {
    }
}
