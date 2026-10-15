package com.example.savedog.Safety.analyzer;

import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;

// score: 1~5(높을수록 위험), reason·hazardType: 주의 이상일 때의 판정 근거(안전이면 null)
public record SafetyAssessment(
        SafetyLevel level,
        int score,
        String reason,
        HazardType hazardType
) {
}
