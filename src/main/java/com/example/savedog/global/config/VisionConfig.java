package com.example.savedog.global.config;

import com.example.savedog.Vision.detector.ObjectDetector;
import com.example.savedog.Vision.detector.OnnxYoloDetector;
import com.example.savedog.Vision.detector.UnconfiguredObjectDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// 카메라 물체 인식 설정. 모델 파일이 없거나 열지 못해도 서버는 뜨고, 위험도는 거리만으로 판단한다.
@Slf4j
@Configuration
public class VisionConfig {

    @Bean
    public ObjectDetector objectDetector(@Value("${savedog.vision.model-path:}") String modelPath) {
        if (modelPath.isBlank()) {
            log.warn("물체 인식 모델 경로(VISION_MODEL_PATH)가 비어 있어 카메라 판단 없이 시작합니다.");
            return new UnconfiguredObjectDetector();
        }
        Path model = Path.of(modelPath.trim()).toAbsolutePath();
        if (!Files.isRegularFile(model)) {
            log.warn("물체 인식 모델 파일이 없어 카메라 판단 없이 시작합니다: {}", model);
            return new UnconfiguredObjectDetector();
        }
        try {
            OnnxYoloDetector detector = new OnnxYoloDetector(model);
            log.info("물체 인식 모델을 불러왔습니다: {} ({})", model.getFileName(), detector.description());
            return detector;
        } catch (Exception | LinkageError e) {
            // 모델 파일이 깨졌거나, 이 컴퓨터에서 ONNX Runtime을 실행할 수 없는 경우
            log.warn("물체 인식 모델을 열지 못해 카메라 판단 없이 시작합니다: {} ({})", model, e.toString());
            return new UnconfiguredObjectDetector();
        }
    }

    /**
     * 사진 분석 전용 작업 스레드 하나. 분석은 수백 ms 걸리므로 사진을 받는 요청과 따로 돌린다.
     * 기다리는 작업은 VisionService가 많아야 하나로 유지한다.
     */
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService visionExecutor() {
        return new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "vision");
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }
}
