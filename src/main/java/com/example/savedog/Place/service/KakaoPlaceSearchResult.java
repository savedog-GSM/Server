package com.example.savedog.Place.service;

import java.util.List;

record KakaoPlaceSearchResult(List<KakaoPlace> places, boolean hasMore) {
}

record KakaoPlace(
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
