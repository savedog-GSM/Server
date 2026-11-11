package com.example.savedog.global.config;

import com.example.savedog.Owner.face.FaceEngine;
import com.example.savedog.Owner.face.OnnxFaceEngine;
import com.example.savedog.Owner.face.UnconfiguredFaceEngine;
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

// 주인 얼굴 인식 모델. 파일이 없거나 열지 못해도 서버는 뜨고, 얼굴 등록만 503으로 응답한다.
@Slf4j
@Configuration
public class FaceConfig {

    @Bean
    public FaceEngine faceEngine(
            @Value("${savedog.face.detector-path:}") String detectorPath,
            @Value("${savedog.face.recognizer-path:}") String recognizerPath
    ) {
        if (detectorPath.isBlank() || recognizerPath.isBlank()) {
            log.warn("얼굴 인식 모델 경로가 비어 있어 얼굴 등록 없이 시작합니다.");
            return new UnconfiguredFaceEngine();
        }
        Path detector = Path.of(detectorPath.trim()).toAbsolutePath();
        Path recognizer = Path.of(recognizerPath.trim()).toAbsolutePath();
        if (!Files.isRegularFile(detector) || !Files.isRegularFile(recognizer)) {
            log.warn("얼굴 인식 모델 파일이 없어 얼굴 등록 없이 시작합니다: {}, {}", detector, recognizer);
            return new UnconfiguredFaceEngine();
        }
        try {
            OnnxFaceEngine engine = new OnnxFaceEngine(detector, recognizer);
            log.info("얼굴 인식 모델을 불러왔습니다: {}, {}", detector.getFileName(), recognizer.getFileName());
            return engine;
        } catch (Exception | LinkageError e) {
            log.warn("얼굴 인식 모델을 열지 못해 얼굴 등록 없이 시작합니다: {}", e.toString());
            return new UnconfiguredFaceEngine();
        }
    }

    // 카메라 사진에서 주인을 찾는 작업 스레드 하나. 물체 인식(vision)과 따로 돌린다.
    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService ownerSightingExecutor() {
        return new ThreadPoolExecutor(
                1, 1, 0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "owner-sighting");
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }
}
