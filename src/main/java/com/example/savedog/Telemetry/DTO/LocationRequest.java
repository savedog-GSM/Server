package com.example.savedog.Telemetry.DTO;

// 스마트폰 브라우저의 위치 기능이 준 값. accuracy(m)는 선택. 그 밖의 값(speed 등)은 보내도 무시한다.
public record LocationRequest(
        Double latitude,
        Double longitude,
        Double accuracy
) {
}
