package com.example.savedog.Place.DTO;

import java.util.List;

public record NearbyPlaceResponse(
        String type,
        double latitude,
        double longitude,
        int radiusMeters,
        int page,
        boolean hasMore,
        List<PlaceResponse> places
) {
}
