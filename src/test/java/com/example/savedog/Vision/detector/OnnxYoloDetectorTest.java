package com.example.savedog.Vision.detector;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// 저장소의 models/yolox_tiny.onnx로 실제 모델을 돌린다. 모델 파일이 없는 곳(CI 등)에서는 건너뛴다.
class OnnxYoloDetectorTest {
    private static final Path MODEL = Path.of("models", "yolox_tiny.onnx");
    private static OnnxYoloDetector detector;

    @BeforeAll
    static void load() throws Exception {
        assumeTrue(Files.isRegularFile(MODEL), "모델 파일이 없어 건너뜀: " + MODEL.toAbsolutePath());
        detector = new OnnxYoloDetector(MODEL);
    }

    @AfterAll
    static void close() {
        if (detector != null) {
            detector.close();
        }
    }

    @Test
    @DisplayName("YOLOX 출력 모양을 알아보고 입력 크기를 모델에서 읽는다")
    void readsModelShape() {
        assertTrue(detector.description().equals("YOLOX, 입력 416x416"), detector.description());
    }

    @Test
    @DisplayName("빈 사진에서는 확실한 물체를 찾지 않고, 가로로 긴 사진도 좌표가 0~1 안에 들어온다")
    void emptyAndWideImages() {
        BufferedImage gray = filled(640, 480, new Color(120, 120, 120));
        List<Detection> none = detector.detect(gray);
        assertTrue(none.stream().noneMatch(detection -> detection.confidence() >= 0.45), none.toString());

        BufferedImage wide = filled(1280, 360, new Color(30, 90, 160));
        for (Detection detection : detector.detect(wide)) {
            assertTrue(detection.left() >= 0 && detection.right() <= 1 && detection.left() <= detection.right(), detection.toString());
            assertTrue(detection.top() >= 0 && detection.bottom() <= 1 && detection.top() <= detection.bottom(), detection.toString());
        }
    }

    @Test
    @DisplayName("작은 사진(1×1)도 오류 없이 처리한다")
    void tinyImage() {
        detector.detect(filled(1, 1, Color.WHITE));
    }

    private static BufferedImage filled(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }
}
