package com.example.savedog.Safety.analyzer;

/**
 * 한 지점의 센서값·카메라 프레임으로 안전도를 판단한다.
 * 규칙 기반이든 AI든 이 인터페이스만 구현하면 SafetyService는 그대로 쓸 수 있다.
 * 구현체를 둘 이상 두면 실제로 쓸 쪽에 @Primary를 붙인다.
 */
public interface SafetyAnalyzer {
    SafetyAssessment analyze(SafetyContext context);
}
