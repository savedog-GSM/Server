package com.example.savedog.global.common;

// WGS84 좌표. Kakao Maps의 LatLng(위도, 경도)와 같은 순서.
public record GeoPoint(
        double latitude,
        double longitude
) {
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    // 두 좌표 사이의 지표면 거리(m). 하버사인 공식
    public double distanceTo(GeoPoint other) {
        double latitudeDelta = Math.toRadians(other.latitude - latitude);
        double longitudeDelta = Math.toRadians(other.longitude - longitude);
        double a = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}
