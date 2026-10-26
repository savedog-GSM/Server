package com.example.savedog.Vision.detector;

import com.example.savedog.Vision.detector.YoloDecoder.Candidate;
import com.example.savedog.Vision.detector.YoloDecoder.Format;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YoloDecoderTest {
    private static final int CLASSES = 80;
    // 416 입력: 52² + 26² + 13² = 3549칸, 640 입력: 80² + 40² + 20² = 8400칸
    private static final int YOLOX_CELLS = 3549;

    @Test
    @DisplayName("출력 모양으로 YOLOX·YOLOv5·YOLOv8을 구분하고, 모르는 모양이면 거부한다")
    void formatByShape() {
        assertEquals(Format.YOLOX, YoloDecoder.formatOf(new long[]{1, 3549, 85}, 416, CLASSES));
        assertEquals(Format.YOLOV5, YoloDecoder.formatOf(new long[]{1, 25200, 85}, 640, CLASSES));
        assertEquals(Format.YOLOV8, YoloDecoder.formatOf(new long[]{1, 84, 8400}, 640, CLASSES));

        assertThrows(IllegalArgumentException.class, () -> YoloDecoder.formatOf(new long[]{1, 8400, 85}, 416, CLASSES));
        assertThrows(IllegalArgumentException.class, () -> YoloDecoder.formatOf(new long[]{1, 84, 8400, 1}, 640, CLASSES));
        // 종류 수가 다른 모델(예: 직접 학습한 3종 모델)은 이름표가 맞지 않으므로 받지 않는다.
        assertThrows(IllegalArgumentException.class, () -> YoloDecoder.formatOf(new long[]{1, 7, 8400}, 640, CLASSES));
    }

    @Test
    @DisplayName("YOLOX는 칸 위치와 간격(8·16·32)으로 상자를 풀고, 물체 확률 × 종류 확률을 점수로 쓴다")
    void decodesYoloxGrid() {
        float[][] rows = new float[YOLOX_CELLS][5 + CLASSES];
        // 간격 8 칸들 다음(52² = 2704)부터 간격 16. 그 안에서 (x=3, y=2) 칸은 2704 + 2 × 26 + 3번째
        int row = 2704 + 2 * 26 + 3;
        rows[row][0] = 0.5f;               // 칸 안에서 x 위치
        rows[row][1] = 0.25f;              // 칸 안에서 y 위치
        rows[row][2] = (float) Math.log(4); // 너비 = e^값 × 간격 = 64px
        rows[row][3] = (float) Math.log(2); // 높이 = 32px
        rows[row][4] = 0.9f;               // 물체일 확률
        rows[row][5] = 0.8f;               // 0번 종류(사람) 확률

        List<Candidate> candidates = YoloDecoder.decodeYolox(rows, 416, 0.3);

        assertEquals(1, candidates.size());
        Candidate person = candidates.getFirst();
        assertEquals(0, person.classIndex());
        assertEquals(0.72f, person.score(), 1e-5);
        // 중심 x = (0.5 + 3) × 16 = 56, 중심 y = (0.25 + 2) × 16 = 36
        assertEquals(56 - 32, person.x1(), 1e-4);
        assertEquals(36 - 16, person.y1(), 1e-4);
        assertEquals(56 + 32, person.x2(), 1e-4);
        assertEquals(36 + 16, person.y2(), 1e-4);
    }

    @Test
    @DisplayName("YOLOv5·YOLOv8은 이미 픽셀 값이다. v8은 행·열이 뒤집혀 있고 물체 확률이 없다")
    void decodesAlreadyDecodedFormats() {
        float[][] v5 = new float[2][5 + CLASSES];
        v5[1] = row(100, 50, 20, 40, 0.5f, 2, 0.9f);   // 물체 0.5 × 자동차 0.9 = 0.45
        List<Candidate> fromV5 = YoloDecoder.decodeYolov5(v5, 0.3);
        assertEquals(1, fromV5.size());
        assertEquals(2, fromV5.getFirst().classIndex());
        assertEquals(0.45f, fromV5.getFirst().score(), 1e-5);
        assertEquals(90, fromV5.getFirst().x1(), 1e-4);
        assertEquals(30, fromV5.getFirst().y1(), 1e-4);

        float[][] v8 = new float[4 + CLASSES][3];
        v8[0][2] = 200; v8[1][2] = 100; v8[2][2] = 40; v8[3][2] = 60;
        v8[4 + 56][2] = 0.7f;                         // 56번 종류(의자)
        List<Candidate> fromV8 = YoloDecoder.decodeYolov8(v8, 0.3);
        assertEquals(1, fromV8.size());
        assertEquals(56, fromV8.getFirst().classIndex());
        assertEquals(180, fromV8.getFirst().x1(), 1e-4);
        assertEquals(130, fromV8.getFirst().y2(), 1e-4);
    }

    @Test
    @DisplayName("기준 점수보다 낮거나 값이 깨진(NaN·무한대) 상자는 버린다")
    void dropsWeakAndBrokenBoxes() {
        float[][] rows = new float[3][5 + CLASSES];
        rows[0] = row(10, 10, 5, 5, 1f, 0, 0.2f);
        rows[1] = row(Float.NaN, 10, 5, 5, 1f, 0, 0.9f);
        rows[2] = row(10, 10, Float.POSITIVE_INFINITY, 5, 1f, 0, 0.9f);

        assertTrue(YoloDecoder.decodeYolov5(rows, 0.3).isEmpty());
    }

    @Test
    @DisplayName("같은 종류끼리 많이 겹친 상자는 점수가 높은 하나만 남기고, 다른 종류는 조금 겹치면 남기되 거의 같은 자리면 버린다")
    void suppressesOverlaps() {
        Candidate strong = new Candidate(0, 0.9f, 0, 0, 100, 100);
        Candidate weakSame = new Candidate(0, 0.6f, 20, 20, 120, 120);       // 겹침 약 47%
        Candidate otherClass = new Candidate(1, 0.5f, 20, 20, 120, 120);     // 겹침 약 47%
        Candidate sameSpotOtherClass = new Candidate(2, 0.55f, 2, 2, 101, 101); // 겹침 약 95% (한 물체를 다른 이름으로)
        Candidate farAway = new Candidate(0, 0.4f, 300, 300, 350, 350);

        List<Candidate> kept = YoloDecoder.suppressOverlaps(
                List.of(weakSame, farAway, strong, sameSpotOtherClass, otherClass), 0.45);

        assertEquals(List.of(strong, otherClass, farAway), kept);
        assertEquals(0, YoloDecoder.overlap(strong, farAway));
        assertEquals(1, YoloDecoder.overlap(strong, strong), 1e-9);
    }

    private static float[] row(float centerX, float centerY, float width, float height,
                               float objectness, int classIndex, float classScore) {
        float[] values = new float[5 + CLASSES];
        values[0] = centerX;
        values[1] = centerY;
        values[2] = width;
        values[3] = height;
        values[4] = objectness;
        values[5 + classIndex] = classScore;
        return values;
    }
}
