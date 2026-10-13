package com.example.savedog.Safety.analyzer;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

// 센서 임계값으로 판단하는 기본 구현체. 기준 초안은 기능명세서 F-06 참고
@Component
public class RuleBasedSafetyAnalyzer implements SafetyAnalyzer {

    @Override
    public SafetyAssessment analyze(SafetyContext context) {
        // TODO(F-06): 거리·기울기별 위험 점수(1~5) 계산 → 더 높은 점수를 위험도로 → SafetyLevel.fromRiskScore로 등급 결정
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }
}
