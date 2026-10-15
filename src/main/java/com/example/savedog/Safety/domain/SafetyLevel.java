package com.example.savedog.Safety.domain;

// 위험 등급. 뒤로 갈수록 위험
public enum SafetyLevel {
    SAFE,
    CAUTION,
    DANGER;

    // 위험도 점수(1~5, 높을수록 위험)를 등급으로 바꾼다. 안전 1~2, 주의 3~4, 위험 5.
    // 여러 기록을 평균해 소수가 된 점수는 반올림해서 본다.
    public static SafetyLevel fromRiskScore(double riskScore) {
        long score = Math.round(riskScore);
        if (score >= 5) {
            return DANGER;
        }
        if (score >= 3) {
            return CAUTION;
        }
        return SAFE;
    }
}
