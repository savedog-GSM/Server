package com.example.savedog.Safety.domain;

// 위험의 종류. 신고의 위험 유형으로 쓰인다.
public enum HazardType {
    // 전방 거리 기준에 걸림
    OBSTACLE,
    // 노면 파손·급경사. 중계 프로그램이 노면 파손(GROUND_DAMAGE)을 신고하면 이 값이 된다.
    // 기울기 판단은 없앴으므로 센서 값으로는 만들어지지 않는다.
    ROUGH_ROAD,
    // 화재. 중계 프로그램의 화재(FIRE) 신고
    FIRE,
    ETC
}
