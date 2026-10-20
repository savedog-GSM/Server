package com.example.savedog.Safety.domain;

// 위험의 종류. 신고의 위험 유형으로 쓰인다.
public enum HazardType {
    // 전방 거리 기준에 걸림
    OBSTACLE,
    // 기울기 기준에 걸림 (급경사, 노면 불량).
    // 기울기 판단을 없애서 새로 만들어지지는 않는다. 예전에 저장된 신고를 읽을 수 있게 남겨 둔다.
    ROUGH_ROAD,
    ETC
}
