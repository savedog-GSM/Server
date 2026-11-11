package com.example.savedog.Owner.service;

import com.example.savedog.Owner.face.FaceDetection;
import com.example.savedog.Owner.face.FaceEngine;
import com.example.savedog.Owner.repository.OwnerFaceRepository;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OwnerSightingServiceTest {
    private static final float[] OWNER = unit(0);
    private static final float[] STRANGER = unit(1);
    // 사진 1000×500에서 오른쪽(가로 700~800)에 있는 얼굴과 왼쪽(100~150)에 있는 얼굴
    private static final FaceDetection RIGHT_FACE = new FaceDetection(0.9, 700, 200, 100, 120, new double[10]);
    private static final FaceDetection LEFT_FACE = new FaceDetection(0.9, 100, 50, 50, 60, new double[10]);
    private static final BufferedImage FRAME = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_RGB);

    private final MutableClock clock = new MutableClock();

    @Test
    @DisplayName("사진 속 얼굴 중 주인과 가장 닮은 얼굴의 위치를 알려 준다 (오른쪽, 가로 +0.5)")
    void findsOwnerPosition() {
        OwnerSightingService service = serviceWith(List.of(LEFT_FACE, RIGHT_FACE), Map.of(LEFT_FACE, STRANGER, RIGHT_FACE, OWNER));

        OwnerSighting sighting = service.sight(FRAME, List.of(OWNER), LocalDateTime.now(clock));

        assertTrue(sighting.ownerVisible());
        assertEquals(2, sighting.facesInFrame());
        assertEquals(1.0, sighting.similarity());
        // 얼굴 가운데 x = 750 → (750 / 1000) × 2 − 1 = 0.5, y = 260 → 0.04
        assertEquals(0.5, sighting.horizontalOffset());
        assertEquals(0.04, sighting.verticalOffset());
        assertEquals(OwnerSighting.Direction.RIGHT, sighting.direction());
        assertEquals(0.1, sighting.faceWidthRatio());
    }

    @Test
    @DisplayName("주인이 아닌 얼굴만 있으면 보이지 않는다고 하고, 얼굴이 없으면 위치가 비어 있다")
    void strangerOrNoFace() {
        OwnerSighting stranger = serviceWith(List.of(LEFT_FACE), Map.of(LEFT_FACE, STRANGER))
                .sight(FRAME, List.of(OWNER), LocalDateTime.now(clock));
        assertFalse(stranger.ownerVisible());
        assertEquals(OwnerSighting.Direction.LEFT, stranger.direction());

        OwnerSighting nobody = serviceWith(List.of(), Map.of()).sight(FRAME, List.of(OWNER), LocalDateTime.now(clock));
        assertFalse(nobody.ownerVisible());
        assertEquals(0, nobody.facesInFrame());
        assertNull(nobody.horizontalOffset());
    }

    private OwnerSightingService serviceWith(List<FaceDetection> faces, Map<FaceDetection, float[]> embeddings) {
        FaceEngine engine = new FaceEngine() {
            @Override
            public List<FaceDetection> detect(BufferedImage image) {
                return faces;
            }

            @Override
            public float[] embed(BufferedImage image, FaceDetection face) {
                return embeddings.get(face);
            }
        };
        return new OwnerSightingService(engine, mock(OwnerFaceRepository.class), Runnable::run, clock);
    }

    private static float[] unit(int index) {
        float[] vector = new float[128];
        vector[index] = 1;
        return vector;
    }
}
