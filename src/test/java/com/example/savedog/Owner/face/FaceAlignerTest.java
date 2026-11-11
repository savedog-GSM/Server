package com.example.savedog.Owner.face;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FaceAlignerTest {

    @Test
    @DisplayName("기준 자리를 2배 키우고 30° 돌리고 옮긴 특징점이면, 그 반대 변환(축소·회전·이동)을 정확히 찾는다")
    void findsSimilarityTransform() {
        double scale = 2, angle = Math.toRadians(30), offsetX = 100, offsetY = 40;
        double[] landmarks = new double[10];
        for (int i = 0; i < 5; i++) {
            double x = FaceAligner.TEMPLATE[2 * i];
            double y = FaceAligner.TEMPLATE[2 * i + 1];
            landmarks[2 * i] = scale * (Math.cos(angle) * x - Math.sin(angle) * y) + offsetX;
            landmarks[2 * i + 1] = scale * (Math.sin(angle) * x + Math.cos(angle) * y) + offsetY;
        }

        double[] t = FaceAligner.similarityTransform(landmarks);

        for (int i = 0; i < 5; i++) {
            double x = landmarks[2 * i];
            double y = landmarks[2 * i + 1];
            assertEquals(FaceAligner.TEMPLATE[2 * i], t[0] * x - t[1] * y + t[2], 1e-9);
            assertEquals(FaceAligner.TEMPLATE[2 * i + 1], t[1] * x + t[0] * y + t[3], 1e-9);
        }
        assertEquals(1 / scale, Math.hypot(t[0], t[1]), 1e-9);
    }

    @Test
    @DisplayName("잘라 낸 112×112 사진의 각 칸은 원본의 대응하는 자리 색을 가져온다")
    void warpsPixels() {
        // 원본: 특징점이 기준 자리 + (50, 30)에 있는 사진. 색으로 원본 좌표를 알 수 있게 칠한다.
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 300; y++) {
            for (int x = 0; x < 300; x++) {
                image.setRGB(x, y, (x << 8) | y);
            }
        }
        double[] landmarks = new double[10];
        for (int i = 0; i < 5; i++) {
            landmarks[2 * i] = FaceAligner.TEMPLATE[2 * i] + 50;
            landmarks[2 * i + 1] = FaceAligner.TEMPLATE[2 * i + 1] + 30;
        }

        BufferedImage aligned = FaceAligner.align(image, landmarks);

        assertEquals(112, aligned.getWidth());
        int rgb = aligned.getRGB(20, 70);
        assertEquals(20 + 50, (rgb >> 8) & 0xFF);
        assertEquals(70 + 30, rgb & 0xFF);
    }

    @Test
    @DisplayName("특징점이 한 점에 모여 있으면 정렬할 수 없다")
    void degenerateLandmarks() {
        assertThrows(IllegalArgumentException.class, () -> FaceAligner.similarityTransform(new double[10]));
    }
}
