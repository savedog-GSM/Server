package com.example.savedog.Safety.analyzer;

import com.example.savedog.Safety.domain.SafetyLevel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// 센서 임계값으로 판단하는 기본 구현체. 기준 초안은 기능명세서 F-06 참고
@Component
public class RuleBasedSafetyAnalyzer implements SafetyAnalyzer {
    // 경계를 하나 넘을 때마다 1점씩 오른다. (1점에서 시작해 최대 5점)
    private static final double[] DISTANCE_LIMITS_CM = {100, 50, 35, 20};
    private static final double[] TILT_LIMITS_DEGREES = {5, 15, 22, 30};
    // 이 점수(주의)부터 판정 근거를 남긴다.
    private static final int REASON_FROM_SCORE = 3;

    @Override
    public SafetyAssessment analyze(SafetyContext context) {
        int distanceScore = scoreByDistance(context.distance());
        int tiltScore = scoreByTilt(largerTilt(context));
        int score = Math.max(distanceScore, tiltScore);
        return new SafetyAssessment(
                SafetyLevel.fromRiskScore(score),
                score,
                describe(context, distanceScore, tiltScore, score)
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

    // 많이 기울수록 위험: 5° 미만 1점, 30° 이상 5점
    private int scoreByTilt(double tilt) {
        int score = 1;
        for (double limit : TILT_LIMITS_DEGREES) {
            if (tilt >= limit) {
                score++;
            }
        }
        return score;
    }

    private double largerTilt(SafetyContext context) {
        return Math.max(Math.abs(context.roll()), Math.abs(context.pitch()));
    }

    // 최종 점수를 만든 항목만 근거로 적는다. 안전(1~2점)이면 근거가 없다.
    private String describe(SafetyContext context, int distanceScore, int tiltScore, int score) {
        if (score < REASON_FROM_SCORE) {
            return null;
        }
        List<String> reasons = new ArrayList<>();
        if (distanceScore == score) {
            reasons.add("전방 %dcm 장애물".formatted(Math.round(context.distance())));
        }
        if (tiltScore == score) {
            boolean rollIsLarger = Math.abs(context.roll()) >= Math.abs(context.pitch());
            reasons.add(rollIsLarger
                    ? "좌우 기울기 %d°".formatted(Math.round(Math.abs(context.roll())))
                    : "앞뒤 기울기 %d°".formatted(Math.round(Math.abs(context.pitch()))));
        }
        return String.join(", ", reasons);
    }
}
