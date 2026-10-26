package com.example.savedog.Vision.service;

import com.example.savedog.Vision.detector.Detection;
import com.example.savedog.Vision.detector.ObjectDetector;
import com.example.savedog.Vision.detector.UnconfiguredObjectDetector;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionServiceTest {
    private static final Detection CLOSE_PERSON = new Detection("person", "사람", 0.9, 0.2, 0.2, 0.8, 0.95);

    private final MutableClock clock = new MutableClock();
    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final FakeDetector detector = new FakeDetector();
    private final VisionService service = new VisionService(detector, tasks::add, clock);

    @Test
    @DisplayName("사진을 분석해 결과를 남기고, 받은 지 3초가 지나면 위험도 판단에는 쓰지 않는다")
    void analyzesAndExpires() throws IOException {
        detector.answer = List.of(CLOSE_PERSON);

        assertTrue(service.submit(jpeg(64, 48)));
        runAll();

        VisionResult result = service.latest().orElseThrow();
        assertEquals(List.of(CLOSE_PERSON), result.detections());
        assertEquals(64, detector.seenWidths.getFirst());
        assertEquals(5, service.findRecentFinding().orElseThrow().score());

        clock.advanceSeconds(3);
        assertTrue(service.findRecentFinding().isPresent());
        clock.advanceSeconds(1);
        assertTrue(service.findRecentFinding().isEmpty());
        // 조회용 최근 결과는 남아 있다.
        assertTrue(service.latest().isPresent());
    }

    @Test
    @DisplayName("분석이 밀리면 기다리는 사진 중 가장 최근 것 하나만 분석한다")
    void analyzesOnlyTheNewestWaitingFrame() throws IOException {
        service.submit(jpeg(10, 10));
        service.submit(jpeg(20, 20));
        service.submit(jpeg(30, 30));

        assertEquals(1, tasks.size());
        runAll();

        assertEquals(List.of(30), detector.seenWidths);
    }

    @Test
    @DisplayName("모델이 없으면 사진을 받지 않고(false) 결과도 없다")
    void unavailableModel() throws IOException {
        VisionService off = new VisionService(new UnconfiguredObjectDetector(), tasks::add, clock);

        assertFalse(off.available());
        assertFalse(off.submit(jpeg(10, 10)));
        assertTrue(tasks.isEmpty());
        assertTrue(off.findRecentFinding().isEmpty());
    }

    @Test
    @DisplayName("깨진 사진이나 모델 오류는 그 사진만 건너뛰고 다음 사진은 분석한다")
    void survivesBrokenFramesAndDetectorErrors() throws IOException {
        // JPEG 머리말만 있고 내용이 깨진 사진
        service.submit(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0});
        runAll();
        assertTrue(service.latest().isEmpty());

        detector.failing = true;
        service.submit(jpeg(10, 10));
        runAll();
        assertTrue(service.latest().isEmpty());

        detector.failing = false;
        service.submit(jpeg(10, 10));
        runAll();
        assertTrue(service.latest().isPresent());
    }

    @Test
    @DisplayName("작업을 걸지 못하면(서버 종료 중 등) false를 돌려주고 다음 사진은 다시 시도한다")
    void rejectedExecution() throws IOException {
        VisionService rejecting = new VisionService(detector, task -> {
            throw new java.util.concurrent.RejectedExecutionException("shutting down");
        }, clock);

        assertFalse(rejecting.submit(jpeg(10, 10)));
        assertFalse(rejecting.submit(jpeg(10, 10)));
    }

    private void runAll() {
        while (!tasks.isEmpty()) {
            tasks.poll().run();
        }
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private static class FakeDetector implements ObjectDetector {
        private final List<Integer> seenWidths = new ArrayList<>();
        private List<Detection> answer = List.of();
        private boolean failing;

        @Override
        public List<Detection> detect(BufferedImage image) {
            if (failing) {
                throw new IllegalStateException("모델 실행 실패");
            }
            seenWidths.add(image.getWidth());
            return answer;
        }
    }
}
