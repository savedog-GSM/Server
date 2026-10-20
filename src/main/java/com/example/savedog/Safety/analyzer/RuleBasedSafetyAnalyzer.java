package com.example.savedog.Safety.analyzer;

import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import org.springframework.stereotype.Component;

// 전방 거리 임계값으로 판단하는 기본 구현체. 기준 초안은 기능명세서 F-06 참고
@Component
public class RuleBasedSafetyAnalyzer implements SafetyAnalyzer {
    // 경계를 하나 넘을 때마다 1점씩 오른다. (1점에서 시작해 최대 5점)
    private static final double[] DISTANCE_LIMITS_CM = {100, 50, 35, 20};
    // 이 점수(주의)부터 판정 근거를 남긴다.
    private static final int REASON_FROM_SCORE = 3;

    @Override
    public SafetyAssessment analyze(SafetyContext context) {
        int score = scoreByDistance(context.distance());
        boolean explain = score >= REASON_FROM_SCORE;
        return new SafetyAssessment(
                SafetyLevel.fromRiskScore(score),
                score,
                explain ? "전방 %dcm 장애물".formatted(Math.round(context.distance())) : null,
                explain ? HazardType.OBSTACLE : null
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
