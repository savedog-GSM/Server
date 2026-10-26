package com.example.savedog.Safety.analyzer;

import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Vision.detector.Detection;
import com.example.savedog.Vision.service.VisionFinding;
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

    @Test
    @DisplayName("카메라 점수가 거리 점수보다 높으면 카메라 점수를 쓰고 근거도 카메라로 남긴다")
    void cameraRaisesScore() {
        SafetyAssessment assessment = analyzer.analyze(new SafetyContext(null, 150, camera(5, "사람")));

        assertEquals(5, assessment.score());
        assertEquals(SafetyLevel.DANGER, assessment.level());
        assertEquals("카메라: 전방 사람 (신뢰도 90%)", assessment.reason());
        assertEquals(HazardType.OBSTACLE, assessment.hazardType());
    }

    @Test
    @DisplayName("둘 다 주의 이상이면 높은 점수를 쓰고 근거를 함께 적는다")
    void bothReasons() {
        SafetyAssessment assessment = analyzer.analyze(new SafetyContext(null, 18.4, camera(3, "의자")));

        assertEquals(5, assessment.score());
        assertEquals("전방 18cm 장애물, 카메라: 전방 의자 (신뢰도 90%)", assessment.reason());
    }

    @Test
    @DisplayName("카메라가 안전(2점 이하)이면 거리 판단 그대로다")
    void safeCameraKeepsDistanceResult() {
        SafetyAssessment safe = analyzer.analyze(new SafetyContext(null, 150, new VisionFinding(2, null, null)));
        assertEquals(2, safe.score());
        assertNull(safe.reason());
        assertNull(safe.hazardType());

        SafetyAssessment byDistance = analyzer.analyze(new SafetyContext(null, 40, new VisionFinding(1, null, null)));
        assertEquals(3, byDistance.score());
        assertEquals("전방 40cm 장애물", byDistance.reason());
    }

    private static VisionFinding camera(int score, String name) {
        Detection cause = new Detection("x", name, 0.9, 0.3, 0.3, 0.7, 0.9);
        return new VisionFinding(score, "카메라: 전방 %s (신뢰도 90%%)".formatted(name), cause);
    }

    private SafetyAssessment analyze(double distance) {
        return analyzer.analyze(new SafetyContext(null, distance));
    }
}
