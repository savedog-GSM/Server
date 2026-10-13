package com.example.savedog.Safety.DTO;

import com.example.savedog.Safety.domain.SafetyLevel;

// score: 1~5(높을수록 위험), reason: 판정 근거(없으면 null)
public record RiskResponse(
        SafetyLevel level,
        int score,
        String reason
) {
}
