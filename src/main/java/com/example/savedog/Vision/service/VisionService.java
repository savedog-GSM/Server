package com.example.savedog.Vision.service;

import com.example.savedog.Vision.detector.Detection;
import com.example.savedog.Vision.detector.ObjectDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 중계 프로그램이 보낸 카메라 사진을 물체 인식 모델로 분석해 최근 결과를 들고 있는다.
 * 분석은 사진 수신과 따로 돈다. 분석이 밀리면 기다리는 사진 중 가장 최근 것 하나만 분석한다.
 * 결과는 위험도 판단(SafetyService)이 센서 값을 받을 때마다 가져다 쓴다.
 */
@Slf4j
@Service
public class VisionService {
    // 받은 지 이보다 오래된 사진의 결과는 위험도 판단에 쓰지 않는다.
    private static final Duration FRESH_FOR = Duration.ofSeconds(3);

    private final ObjectDetector detector;
    private final Executor executor;
    private final Clock clock;
    private final AtomicReference<Frame> waiting = new AtomicReference<>();
    private final AtomicReference<VisionResult> latest = new AtomicReference<>();

    public VisionService(
            ObjectDetector detector,
            @Qualifier("visionExecutor") Executor executor,
            Clock clock
    ) {
        this.detector = detector;
        this.executor = executor;
        this.clock = clock;
    }

    public boolean available() {
        return detector.available();
    }

    /**
     * 분석할 사진을 넘긴다. 모델이 없으면 false.
     * jpeg는 CameraFrameService가 형식·크기를 확인한 것이다.
     */
    public boolean submit(byte[] jpeg) {
        if (!detector.available()) {
            return false;
        }
        // 기다리던 사진이 없었을 때만 분석 작업을 새로 건다. 있었으면 그 자리를 새 사진으로 바꾸기만 한다.
        if (waiting.getAndSet(new Frame(jpeg, LocalDateTime.now(clock))) == null) {
            try {
                executor.execute(this::analyzeWaiting);
            } catch (RejectedExecutionException e) {
                waiting.set(null);
                log.warn("카메라 사진 분석 작업을 시작하지 못했습니다: {}", e.toString());
                return false;
            }
        }
        return true;
    }

    public Optional<VisionResult> latest() {
        return Optional.ofNullable(latest.get());
    }

    // 위험도 판단에 써도 되는 최근 결과. 받은 지 3초 이내 사진의 것만 준다.
    public Optional<VisionFinding> findRecentFinding() {
        return latest().filter(this::isFresh).map(VisionResult::finding);
    }

    public boolean isFresh(VisionResult result) {
        return Duration.between(result.frameReceivedAt(), LocalDateTime.now(clock)).compareTo(FRESH_FOR) <= 0;
    }

    private void analyzeWaiting() {
        Frame frame;
        while ((frame = waiting.getAndSet(null)) != null) {
            analyze(frame);
        }
    }

    // 다른 스레드에서 돌기 때문에 여기서 잡지 않으면 실패가 아무 데도 남지 않는다.
    private void analyze(Frame frame) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame.jpeg()));
            if (image == null) {
                log.warn("카메라 사진을 이미지로 읽지 못했습니다. (JPEG 내용이 깨졌을 수 있습니다)");
                return;
            }
            long started = System.nanoTime();
            List<Detection> detections = detector.detect(image);
            long elapsedMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            latest.set(new VisionResult(
                    frame.receivedAt(),
                    LocalDateTime.now(clock),
                    elapsedMs,
                    detections,
                    PathObstacleRule.evaluate(detections)
            ));
        } catch (IOException | RuntimeException e) {
            log.warn("카메라 사진 분석 실패: {}", e.toString());
        }
    }

    private record Frame(byte[] jpeg, LocalDateTime receivedAt) {
    }
}
