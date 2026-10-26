package com.example.savedog.Vision.DTO;

import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Vision.detector.Detection;
import com.example.savedog.Vision.service.VisionResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 가장 최근에 분석한 카메라 사진의 결과.
 * available: 물체 인식 모델이 켜져 있는지. false거나 아직 분석한 사진이 없으면 나머지는 null(목록은 빈 값)
 * fresh: 받은 지 3초 이내 사진이라 위험도 판단에 쓰이고 있는지
 */
public record VisionResponse(
        boolean available,
        boolean fresh,
        LocalDateTime frameReceivedAt,
        LocalDateTime analyzedAt,
        Long elapsedMs,
        Integer score,
        SafetyLevel level,
        String reason,
        List<Detection> detections
) {
    public static VisionResponse empty(boolean available) {
        return new VisionResponse(available, false, null, null, null, null, null, null, List.of());
    }

    public static VisionResponse of(VisionResult result, boolean fresh) {
        return new VisionResponse(
                true,
                fresh,
                result.frameReceivedAt(),
                result.analyzedAt(),
                result.elapsedMs(),
                result.finding().score(),
                SafetyLevel.fromRiskScore(result.finding().score()),
                result.finding().reason(),
                result.detections()
        );
    }
}
