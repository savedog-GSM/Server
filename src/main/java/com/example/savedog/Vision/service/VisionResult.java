package com.example.savedog.Vision.service;

import com.example.savedog.Vision.detector.Detection;

import java.time.LocalDateTime;
import java.util.List;

// 가장 최근에 분석한 사진 한 장의 결과
public record VisionResult(
        LocalDateTime frameReceivedAt,
        LocalDateTime analyzedAt,
        long elapsedMs,
        List<Detection> detections,
        VisionFinding finding
) {
}
