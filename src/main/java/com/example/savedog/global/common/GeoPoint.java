package com.example.savedog.global.common;

// WGS84 좌표. Kakao Maps의 LatLng(위도, 경도)와 같은 순서.
public record GeoPoint(
        double latitude,
        double longitude
) {
}
