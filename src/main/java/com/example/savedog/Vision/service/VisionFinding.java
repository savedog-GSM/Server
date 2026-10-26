package com.example.savedog.Vision.service;

import com.example.savedog.Vision.detector.Detection;

// 카메라 사진 한 장으로 본 위험도. score는 거리 점수와 같은 1~5, reason·cause는 주의(3점) 이상일 때만 있다.
public record VisionFinding(
        int score,
        String reason,
        Detection cause
) {
    static final VisionFinding CLEAR = new VisionFinding(1, null, null);
}
