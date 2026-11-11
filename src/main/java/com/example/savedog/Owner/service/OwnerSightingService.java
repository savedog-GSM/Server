package com.example.savedog.Owner.service;

import com.example.savedog.Owner.domain.OwnerFace;
import com.example.savedog.Owner.face.FaceDetection;
import com.example.savedog.Owner.face.FaceEngine;
import com.example.savedog.Owner.repository.OwnerFaceRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 카메라 사진(POST /api/robot/frames)에서 등록된 주인 얼굴을 찾아 최근 결과를 들고 있는다.
 * 분석은 사진 수신과 따로 돈다. 밀리면 기다리는 사진 중 가장 최근 것 하나만 분석한다. (VisionService와 같은 방식)
 * 주인 얼굴이 등록되어 있지 않으면 분석하지 않는다.
 */
@Slf4j
@Service
public class OwnerSightingService {
    // 받은 지 이보다 오래된 사진의 결과는 "지금 보인다"로 치지 않는다.
    private static final Duration FRESH_FOR = Duration.ofSeconds(3);
    // 한 사진에서 비교할 얼굴 수. 큰(가까운) 얼굴부터 본다.
    private static final int MAX_FACES_TO_COMPARE = 5;
    private static final double CENTER_BAND = 0.2;

    private final FaceEngine faceEngine;
    private final OwnerFaceRepository ownerFaceRepository;
    private final Executor executor;
    private final Clock clock;
    private final AtomicReference<Frame> waiting = new AtomicReference<>();
    private final AtomicReference<OwnerSighting> latest = new AtomicReference<>();

    public OwnerSightingService(
            FaceEngine faceEngine,
            OwnerFaceRepository ownerFaceRepository,
            @Qualifier("ownerSightingExecutor") Executor executor,
            Clock clock
    ) {
        this.faceEngine = faceEngine;
        this.ownerFaceRepository = ownerFaceRepository;
        this.executor = executor;
        this.clock = clock;
    }

    public boolean available() {
        return faceEngine.available();
    }

    // 형식·크기 검사를 마친 JPEG를 넘긴다. 모델이 없으면 아무것도 하지 않는다.
    public void submit(byte[] jpeg) {
        if (!faceEngine.available()) {
            return;
        }
        if (waiting.getAndSet(new Frame(jpeg, LocalDateTime.now(clock))) == null) {
            try {
                executor.execute(this::analyzeWaiting);
            } catch (RejectedExecutionException e) {
                waiting.set(null);
                log.warn("주인 찾기 분석 작업을 시작하지 못했습니다: {}", e.toString());
            }
        }
    }

    public Optional<OwnerSighting> latest() {
        return Optional.ofNullable(latest.get());
    }

    public boolean isFresh(OwnerSighting sighting) {
        return Duration.between(sighting.frameReceivedAt(), LocalDateTime.now(clock)).compareTo(FRESH_FOR) <= 0;
    }

    // 3초 안에 받은 사진에 주인이 보였으면 그 결과
    public Optional<OwnerSighting> recentSighting() {
        return latest().filter(this::isFresh).filter(OwnerSighting::ownerVisible);
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
            List<float[]> owners = ownerFaceRepository.findAll().stream().map(OwnerFace::embeddingValues).toList();
            if (owners.isEmpty()) {
                return;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(frame.jpeg()));
            if (image == null) {
                return;
            }
            latest.set(sight(image, owners, frame.receivedAt()));
        } catch (IOException | RuntimeException e) {
            log.warn("주인 찾기 분석 실패: {}", e.toString());
        }
    }

    OwnerSighting sight(BufferedImage image, List<float[]> owners, LocalDateTime receivedAt) {
        List<FaceDetection> faces = faceEngine.detect(image);
        FaceDetection best = null;
        double bestSimilarity = Double.NEGATIVE_INFINITY;
        for (FaceDetection face : faces.stream()
                .sorted(Comparator.comparingDouble(FaceDetection::width).reversed())
                .limit(MAX_FACES_TO_COMPARE)
                .toList()) {
            float[] embedding = faceEngine.embed(image, face);
            double similarity = owners.stream()
                    .mapToDouble(owner -> FaceEngine.similarity(embedding, owner))
                    .max()
                    .orElse(Double.NEGATIVE_INFINITY);
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                best = face;
            }
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (best == null) {
            return new OwnerSighting(receivedAt, now, faces.size(), false, null, null, null, null, null);
        }
        double horizontal = clamp((best.x() + best.width() / 2) / image.getWidth() * 2 - 1);
        double vertical = clamp((best.y() + best.height() / 2) / image.getHeight() * 2 - 1);
        OwnerSighting.Direction direction = horizontal < -CENTER_BAND ? OwnerSighting.Direction.LEFT
                : horizontal > CENTER_BAND ? OwnerSighting.Direction.RIGHT
                : OwnerSighting.Direction.CENTER;
        return new OwnerSighting(
                receivedAt,
                now,
                faces.size(),
                bestSimilarity >= FaceEngine.SAME_PERSON_SIMILARITY,
                round(bestSimilarity),
                round(horizontal),
                round(vertical),
                direction,
                round(Math.min(1, best.width() / image.getWidth()))
        );
    }

    private static double clamp(double value) {
        return Math.max(-1, Math.min(1, value));
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    private record Frame(byte[] jpeg, LocalDateTime receivedAt) {
    }
}
