package com.example.savedog.Place.service;

import com.example.savedog.Place.DTO.NearbyPlaceResponse;
import com.example.savedog.Place.DTO.PlaceResponse;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class NearbyPlaceService {
    private static final int DEFAULT_RADIUS_METERS = 1_000;
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 15;
    private static final int MAX_RADIUS_METERS = 20_000;
    private static final int MAX_PAGE = 45;
    private static final int MAX_SIZE = 15;

    private final KakaoPlaceSearchClient kakaoPlaceSearchClient;

    public NearbyPlaceResponse search(
            String type, Double latitude, Double longitude,
            Integer radiusMeters, Integer page, Integer size
    ) {
        GeoPoint center = RequestValidator.coordinate(latitude, longitude, "center");
        int radius = RequestValidator.inRange(
                radiusMeters == null ? DEFAULT_RADIUS_METERS : radiusMeters, 0, MAX_RADIUS_METERS, "radius"
        );
        int requestedPage = RequestValidator.inRange(page == null ? DEFAULT_PAGE : page, 1, MAX_PAGE, "page");
        int requestedSize = RequestValidator.inRange(size == null ? DEFAULT_SIZE : size, 1, MAX_SIZE, "size");
        String normalizedType = RequestValidator.required(type, "type").trim().toLowerCase(Locale.ROOT);

        KakaoPlaceSearchResult result = switch (normalizedType) {
            case "convenience" -> kakaoPlaceSearchClient.searchCategory(
                    "CS2", center.longitude(), center.latitude(), radius, requestedPage, requestedSize
            );
            case "toilet" -> kakaoPlaceSearchClient.searchKeyword(
                    "화장실", center.longitude(), center.latitude(), radius, requestedPage, requestedSize
            );
            default -> throw RequestValidator.invalid("type은 convenience 또는 toilet이어야 합니다.");
        };

        return new NearbyPlaceResponse(
                normalizedType,
                center.latitude(),
                center.longitude(),
                radius,
                requestedPage,
                result.hasMore(),
                result.places().stream().map(this::toResponse).toList()
        );
    }

    private PlaceResponse toResponse(KakaoPlace place) {
        return new PlaceResponse(
                place.id(), place.name(), place.category(), place.categoryGroupCode(), place.categoryGroupName(),
                place.phone(), place.address(), place.roadAddress(), place.latitude(), place.longitude(),
                place.distanceMeters(), place.placeUrl()
        );
    }
}
