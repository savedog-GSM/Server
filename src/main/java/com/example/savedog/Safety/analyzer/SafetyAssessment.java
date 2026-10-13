package com.example.savedog.Safety.analyzer;

import com.example.savedog.Safety.domain.SafetyLevel;

// score: 0~100(높을수록 안전), reason: 판정 근거(없으면 null)
public record SafetyAssessment(
        SafetyLevel level,
        int score,
        String reason
) {
}
