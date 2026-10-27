package com.example.savedog.Place.DTO;

public record PlaceResponse(
        String id,
        String name,
        String category,
        String categoryGroupCode,
        String categoryGroupName,
        String phone,
        String address,
        String roadAddress,
        double latitude,
        double longitude,
        Double distanceMeters,
        String placeUrl
) {
}
