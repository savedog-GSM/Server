package com.example.savedog.Vision.service;

import com.example.savedog.Vision.detector.Detection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PathObstacleRuleTest {

    @Test
    @DisplayName("길 위의 물체는 클수록(가까울수록) 점수가 오른다: 25% 3점, 40% 4점, 60% 5점")
    void scoresBySize() {
        assertEquals(2, PathObstacleRule.evaluate(List.of(person(0.10, 0.15))).score());
        assertEquals(3, PathObstacleRule.evaluate(List.of(person(0.20, 0.27))).score());
        assertEquals(4, PathObstacleRule.evaluate(List.of(person(0.25, 0.42))).score());
        assertEquals(5, PathObstacleRule.evaluate(List.of(person(0.60, 0.65))).score());
    }

    @Test
    @DisplayName("길 옆에 있거나 사진 위쪽에만 보이는 물체, 확실하지 않은 물체는 보지 않는다")
    void ignoresOffPathAndUncertain() {
        Detection leftSide = new Detection("person", "사람", 0.9, 0.0, 0.2, 0.28, 0.95);
        Detection upInTheAir = new Detection("traffic light", "신호등", 0.9, 0.4, 0.0, 0.6, 0.45);
        Detection uncertain = new Detection("chair", "의자", 0.4, 0.3, 0.3, 0.7, 0.95);

        assertSame(VisionFinding.CLEAR, PathObstacleRule.evaluate(List.of(leftSide, upInTheAir, uncertain)));
        assertSame(VisionFinding.CLEAR, PathObstacleRule.evaluate(List.of()));
    }

    @Test
    @DisplayName("주의 이상이면 가장 위험한 물체를 근거로 남긴다")
    void reasonFromMostDangerous() {
        Detection small = person(0.10, 0.15);
        Detection big = new Detection("bicycle", "자전거", 0.823, 0.2, 0.3, 0.85, 0.98);

        VisionFinding finding = PathObstacleRule.evaluate(List.of(small, big));

        assertEquals(5, finding.score());
        assertEquals("카메라: 전방 자전거 (신뢰도 82%)", finding.reason());
        assertSame(big, finding.cause());
        assertNull(PathObstacleRule.evaluate(List.of(small)).reason());
    }

    // 사진 가운데, 아래 끝이 90% 높이에 서 있는 사람. width·height는 사진에서 차지하는 비율
    private static Detection person(double width, double height) {
        double left = 0.5 - width / 2;
        return new Detection("person", "사람", 0.9, left, 0.9 - height, left + width, 0.9);
    }
}
