package com.example.savedog.Safety.analyzer;

import com.example.savedog.Safety.domain.HazardType;
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
    private static final int LOWEST_SCORE = 1;
    // 값이 없는 항목의 점수. 어떤 최종 점수와도 같아질 수 없어서 근거나 위험 유형으로 뽑히지 않는다.
    private static final int NOT_MEASURED = 0;

    // 거리와 기울기는 서로 다른 장치가 보내므로 한쪽이 없을 수 있다. 있는 값만으로 판단한다.
    @Override
    public SafetyAssessment analyze(SafetyContext context) {
        int distanceScore = context.distance() == null ? NOT_MEASURED : scoreByDistance(context.distance());
        int tiltScore = hasTilt(context) ? scoreByTilt(largerTilt(context)) : NOT_MEASURED;
        int score = Math.max(LOWEST_SCORE, Math.max(distanceScore, tiltScore));
        boolean explain = score >= REASON_FROM_SCORE;
        return new SafetyAssessment(
                SafetyLevel.fromRiskScore(score),
                score,
                explain ? describe(context, distanceScore, tiltScore, score) : null,
                explain ? hazardType(distanceScore, score) : null
        );
    }

    // 가까울수록 위험: 100cm 이상 1점, 20cm 미만 5점
    private int scoreByDistance(double distance) {
        int score = LOWEST_SCORE;
        for (double limit : DISTANCE_LIMITS_CM) {
            if (distance < limit) {
                score++;
            }
        }
        return score;
    }

    // 많이 기울수록 위험: 5° 미만 1점, 30° 이상 5점
    private int scoreByTilt(double tilt) {
        int score = LOWEST_SCORE;
        for (double limit : TILT_LIMITS_DEGREES) {
            if (tilt >= limit) {
                score++;
            }
        }
        return score;
    }

    private boolean hasTilt(SafetyContext context) {
        return context.roll() != null && context.pitch() != null;
    }

    private double largerTilt(SafetyContext context) {
        return Math.max(Math.abs(context.roll()), Math.abs(context.pitch()));
    }

    // 거리 기준에 걸렸으면 장애물, 기울기 기준에만 걸렸으면 위험한 길
    private HazardType hazardType(int distanceScore, int score) {
        return distanceScore == score ? HazardType.OBSTACLE : HazardType.ROUGH_ROAD;
    }

    // 최종 점수를 만든 항목만 근거로 적는다.
    private String describe(SafetyContext context, int distanceScore, int tiltScore, int score) {
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
