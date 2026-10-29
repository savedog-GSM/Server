package com.example.savedog.Place.controller;

import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Place.DTO.NearbyPlaceResponse;
import com.example.savedog.Place.DTO.PlaceRouteResponse;
import com.example.savedog.Place.service.NearbyPlaceService;
import com.example.savedog.Place.service.PlaceRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
public class NearbyPlaceController {
    private final NearbyPlaceService nearbyPlaceService;
    private final PlaceRouteService placeRouteService;

    @GetMapping("/nearby")
    public NearbyPlaceResponse nearby(
            @RequestParam String type,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return nearbyPlaceService.search(type, latitude, longitude, radius, page, size);
    }

    // 가까운 화장실·편의점까지 자동 길찾기. 출발지(latitude·longitude)를 빼면 스마트폰의 최근 위치에서 출발하고,
    // mode를 빼면 안전 우선(SAFEST)이다.
    @GetMapping("/nearest/route")
    public PlaceRouteResponse nearestRoute(
            @RequestParam String type,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(required = false) PathMode mode
    ) {
        return placeRouteService.route(type, latitude, longitude, radius, mode);
    }
}
