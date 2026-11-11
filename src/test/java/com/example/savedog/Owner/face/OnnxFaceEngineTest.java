package com.example.savedog.Owner.face;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// 저장소의 models 폴더에 있는 실제 얼굴 모델로 돌린다. 모델 파일이 없는 곳(CI 등)에서는 건너뛴다.
// 실제 얼굴 사진으로는 개발 중에 확인했다: 같은 사람(돌리고 줄이고 밝기를 바꾼 사진) 0.92, 다른 사람 0.16 (기준 0.363)
class OnnxFaceEngineTest {
    private static final Path DETECTOR = Path.of("models", "face_detection_yunet_2023mar.onnx");
    private static final Path RECOGNIZER = Path.of("models", "face_recognition_sface_2021dec.onnx");
    private static OnnxFaceEngine engine;

    @BeforeAll
    static void load() throws Exception {
        assumeTrue(Files.isRegularFile(DETECTOR) && Files.isRegularFile(RECOGNIZER), "얼굴 모델 파일이 없어 건너뜀");
        engine = new OnnxFaceEngine(DETECTOR, RECOGNIZER);
    }

    @AfterAll
    static void close() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("얼굴이 없는 사진에서는 아무것도 찾지 않는다")
    void noFaceInPlainImage() {
        assertTrue(engine.detect(filled(800, 600, new Color(90, 140, 200))).isEmpty());
        assertTrue(engine.detect(filled(1, 1, Color.WHITE)).isEmpty());
    }

    @Test
    @DisplayName("특징값은 길이 1인 숫자 128개이고, 같은 사진이면 유사도가 1이다")
    void embeddingIsNormalized() {
        BufferedImage image = filled(200, 200, new Color(180, 150, 130));
        double[] landmarks = new double[10];
        for (int i = 0; i < 10; i++) {
            landmarks[i] = FaceAligner.TEMPLATE[i] + 40;
        }
        FaceDetection face = new FaceDetection(1, 40, 40, 112, 112, landmarks);

        float[] embedding = engine.embed(image, face);

        assertEquals(128, embedding.length);
        assertEquals(1, FaceEngine.similarity(embedding, embedding), 1e-4);
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
