package com.example.savedog.Place.controller;

import com.example.savedog.Place.DTO.NearbyPlaceResponse;
import com.example.savedog.Place.service.NearbyPlaceService;
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
}
