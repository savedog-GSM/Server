package com.example.savedog.Safety.analyzer;

import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Vision.service.VisionFinding;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 전방 거리 임계값으로 판단하는 기본 구현체. 기준 초안은 기능명세서 F-06 참고
// 최근 카메라 판단이 있으면 거리 점수와 카메라 점수 중 높은 쪽을 쓴다.
@Component
public class RuleBasedSafetyAnalyzer implements SafetyAnalyzer {
    // 경계를 하나 넘을 때마다 1점씩 오른다. (1점에서 시작해 최대 5점)
    private static final double[] DISTANCE_LIMITS_CM = {100, 50, 35, 20};
    // 이 점수(주의)부터 판정 근거를 남긴다.
    private static final int REASON_FROM_SCORE = 3;

    @Override
    public SafetyAssessment analyze(SafetyContext context) {
        int distanceScore = scoreByDistance(context.distance());
        VisionFinding vision = context.vision();
        int visionScore = vision == null ? 1 : vision.score();
        int score = Math.max(distanceScore, visionScore);
        if (score < REASON_FROM_SCORE) {
            return new SafetyAssessment(SafetyLevel.fromRiskScore(score), score, null, null);
        }

        // 둘 다 주의 이상이면 근거를 함께 적는다. 예: "전방 18cm 장애물, 카메라: 전방 사람 (신뢰도 82%)"
        List<String> reasons = new ArrayList<>();
        if (distanceScore >= REASON_FROM_SCORE) {
            reasons.add("전방 %dcm 장애물".formatted(Math.round(context.distance())));
        }
        if (visionScore >= REASON_FROM_SCORE && vision.reason() != null) {
            reasons.add(vision.reason());
        }
        return new SafetyAssessment(
                SafetyLevel.fromRiskScore(score),
                score,
                reasons.isEmpty() ? null : String.join(", ", reasons),
                HazardType.OBSTACLE
        );
    }

    // 가까울수록 위험: 100cm 이상 1점, 20cm 미만 5점
    private int scoreByDistance(double distance) {
        int score = 1;
        for (double limit : DISTANCE_LIMITS_CM) {
            if (distance < limit) {
                score++;
            }
        }
        return score;
    }
}
