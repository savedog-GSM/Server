package com.example.savedog.Safety.analyzer;

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
        SafetyAssessment assessment = analyze(distance, 0, 0);

        assertEquals(expectedScore, assessment.score());
        assertEquals(expectedLevel, assessment.level());
    }

    @ParameterizedTest(name = "roll {0}°, pitch {1}° → {2}점 {3}")
    @DisplayName("기울기가 클수록 점수가 오르고, 방향(부호)과 무관하게 큰 쪽을 본다")
    @CsvSource({
            "0,    0,   1, SAFE",
            "4.9,  0,   1, SAFE",
            "5,    0,   2, SAFE",
            "0,    14.9, 2, SAFE",
            "-15,  0,   3, CAUTION",
            "0,    21.9, 3, CAUTION",
            "3,    22,  4, CAUTION",
            "-29.9, 10, 4, CAUTION",
            "0,    -30, 5, DANGER",
            "45,   0,   5, DANGER"
    })
    void scoresByTilt(double roll, double pitch, int expectedScore, SafetyLevel expectedLevel) {
        SafetyAssessment assessment = analyze(150, roll, pitch);

        assertEquals(expectedScore, assessment.score());
        assertEquals(expectedLevel, assessment.level());
    }

    @Test
    @DisplayName("거리와 기울기 중 더 높은 점수가 위험도가 된다")
    void takesHigherOfDistanceAndTilt() {
        // 거리 40cm는 3점, 기울기 25°는 4점
        SafetyAssessment assessment = analyze(40, 0, 25);

        assertEquals(4, assessment.score());
        assertEquals(SafetyLevel.CAUTION, assessment.level());
        assertEquals("앞뒤 기울기 25°", assessment.reason());
    }

    @Test
    @DisplayName("안전이면 근거를 남기지 않는다")
    void noReasonWhenSafe() {
        assertNull(analyze(150, 0, 0).reason());
        assertNull(analyze(60, 10, 0).reason());
    }

    @Test
    @DisplayName("주의 이상이면 점수를 만든 항목을 근거로 남긴다")
    void reasonNamesWhatRaisedTheScore() {
        assertEquals("전방 18cm 장애물", analyze(18, 0, 0).reason());
        assertEquals("좌우 기울기 32°", analyze(150, -32, 5).reason());
        assertEquals("앞뒤 기울기 32°", analyze(150, 5, 32).reason());
        assertEquals("전방 18cm 장애물, 좌우 기울기 32°", analyze(18, 32, 0).reason());
    }

    private SafetyAssessment analyze(double distance, double roll, double pitch) {
        return analyzer.analyze(new SafetyContext(null, distance, roll, pitch));
    }
}
