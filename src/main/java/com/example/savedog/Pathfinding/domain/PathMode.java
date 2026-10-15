package com.example.savedog.Pathfinding.domain;

public enum PathMode {
    // 위험도를 보지 않고 길이만으로 계산
    SHORTEST,
    // 길이에 위험도 점수를 곱해 비용을 매기고 위험 등급 구간은 지나지 않음
    SAFEST
}
