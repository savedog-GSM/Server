package com.example.savedog.Vision.service;

import com.example.savedog.Vision.detector.Detection;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 걸어갈 길에 있는 물체만 보고, 사진에서 크게 보일수록 가깝다고 판단한다.
 * - 걸어갈 길: 사진 가운데 세로 띠(가로 30~70%)에 걸치고, 물체의 아래 끝이 사진 아래쪽 절반에 있는 것
 *   (위쪽에만 보이는 간판·신호등이나 길 옆의 물체는 빼기 위해서다)
 * - 크기: 물체 상자의 가로·세로 중 긴 쪽이 사진에서 차지하는 비율
 * 기준값은 카메라 높이·각도에 따라 달라지므로 실제 장비에서 보고 조정한다.
 */
final class PathObstacleRule {
    static final double MIN_CONFIDENCE = 0.45;
    private static final double PATH_LEFT = 0.3;
    private static final double PATH_RIGHT = 0.7;
    private static final double GROUND_FROM = 0.5;
    // 크기가 이 값 이상이면 차례대로 3점(주의), 4점(주의), 5점(위험). 길 위에 있지만 더 작으면 2점
    private static final double[] SIZE_LIMITS = {0.25, 0.4, 0.6};
    private static final int CAUTION_SCORE = 3;

    private PathObstacleRule() {
    }

    static VisionFinding evaluate(List<Detection> detections) {
        return detections.stream()
                .filter(detection -> detection.confidence() >= MIN_CONFIDENCE && onPath(detection))
                .max(Comparator.comparingInt(PathObstacleRule::score)
                        .thenComparingDouble(Detection::confidence))
                .map(PathObstacleRule::finding)
                .orElse(VisionFinding.CLEAR);
    }

    static boolean onPath(Detection detection) {
        return detection.right() > PATH_LEFT && detection.left() < PATH_RIGHT && detection.bottom() >= GROUND_FROM;
    }

    static int score(Detection detection) {
        double size = Math.max(detection.width(), detection.height());
        int score = 2;
        for (double limit : SIZE_LIMITS) {
            if (size >= limit) {
                score++;
            }
        }
        return score;
    }

    private static VisionFinding finding(Detection detection) {
        int score = score(detection);
        if (score < CAUTION_SCORE) {
            return new VisionFinding(score, null, null);
        }
        String reason = String.format(Locale.ROOT, "카메라: 전방 %s (신뢰도 %d%%)",
                detection.name(), Math.round(detection.confidence() * 100));
        return new VisionFinding(score, reason, detection);
    }
}
