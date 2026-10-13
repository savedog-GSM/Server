package com.example.savedog.Safety.analyzer;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

// 센서 임계값으로 판단하는 기본 구현체. 규칙 초안은 기능 명세서 6.1 참고
@Component
public class RuleBasedSafetyAnalyzer implements SafetyAnalyzer {

    @Override
    public SafetyAssessment analyze(SafetyContext context) {
        // TODO(F-05): 센서값별 점수 계산 → 가장 낮은 점수를 최종 점수로 → 점수 구간에 따라 등급 결정
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }
}
