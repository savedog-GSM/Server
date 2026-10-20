package com.example.savedog.Safety.analyzer;

import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RuleBasedSafetyAnalyzerTest {
    private final RuleBasedSafetyAnalyzer analyzer = new RuleBasedSafetyAnalyzer();

    @ParameterizedTest(name = "거리 {0}cm → {1}점 {2}")
    @DisplayName("거리가 가까울수록 점수가 오른다")
    @CsvSource({
            "400,  1, SAFE",
            "150,  1, SAFE",
            "100,  1, SAFE",
            "99.9, 2, SAFE",
            "50,   2, SAFE",
            "49.9, 3, CAUTION",
            "35,   3, CAUTION",
            "34.9, 4, CAUTION",
            "20,   4, CAUTION",
            "19.9, 5, DANGER",
            "0,    5, DANGER"
    })
    void scoresByDistance(double distance, int expectedScore, SafetyLevel expectedLevel) {
        SafetyAssessment assessment = analyze(distance);

        assertEquals(expectedScore, assessment.score());
        assertEquals(expectedLevel, assessment.level());
    }

    @Test
    @DisplayName("안전이면 근거와 위험 유형을 남기지 않는다")
    void noReasonWhenSafe() {
        assertNull(analyze(150).reason());
        assertNull(analyze(60).reason());
        assertNull(analyze(60).hazardType());
    }

    @Test
    @DisplayName("주의 이상이면 거리를 근거로 남기고 장애물로 본다")
    void reasonAndHazardTypeFromDistance() {
        SafetyAssessment caution = analyze(40);
        assertEquals("전방 40cm 장애물", caution.reason());
        assertEquals(HazardType.OBSTACLE, caution.hazardType());

        SafetyAssessment danger = analyze(18.4);
        assertEquals("전방 18cm 장애물", danger.reason());
        assertEquals(HazardType.OBSTACLE, danger.hazardType());
    }

    private SafetyAssessment analyze(double distance) {
        return analyzer.analyze(new SafetyContext(null, distance));
    }
}
