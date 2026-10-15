package com.example.savedog.Telemetry.DTO;

// 스마트폰 브라우저의 위치 기능이 준 값. accuracy(m)와 speed(m/s)는 선택
public record LocationRequest(
        Double latitude,
        Double longitude,
        Double accuracy,
        Double speed
) {
}
