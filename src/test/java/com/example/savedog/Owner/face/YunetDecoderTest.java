package com.example.savedog.Owner.face;

import com.example.savedog.Owner.face.YunetDecoder.StrideOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YunetDecoderTest {
    private static final int INPUT = 640;

    @Test
    @DisplayName("칸 위치와 간격으로 상자·특징점을 풀고, 점수는 √(cls × obj)다")
    void decodesOneFace() {
        List<StrideOutput> outputs = emptyOutputs();
        StrideOutput stride16 = outputs.get(1);
        // 간격 16에서 (줄 10, 열 20) 칸 = 10 × 40 + 20
        int cell = 10 * (INPUT / 16) + 20;
        stride16.cls()[cell] = 0.81f;
        stride16.obj()[cell] = 1.0f;
        stride16.bbox()[cell] = new float[]{0.5f, 0.25f, (float) Math.log(4), (float) Math.log(5)};
        stride16.kps()[cell] = new float[]{-1, -1, 1, -1, 0, 0, -1, 1, 1, 1};

        List<FaceDetection> faces = YunetDecoder.decode(outputs, INPUT, 0.7);

        assertEquals(1, faces.size());
        FaceDetection face = faces.getFirst();
        assertEquals(0.9, face.score(), 1e-6);
        // 중심 x = (20 + 0.5) × 16 = 328, 너비 = 4 × 16 = 64
        assertEquals(328 - 32, face.x(), 1e-4);
        assertEquals((10 + 0.25) * 16 - 40, face.y(), 1e-4);
        assertEquals(64, face.width(), 1e-4);
        assertEquals(80, face.height(), 1e-4);
        assertEquals((20 - 1) * 16, face.landmarks()[0], 1e-4);
        assertEquals((10 + 1) * 16, face.landmarks()[9], 1e-4);
    }

    @Test
    @DisplayName("기준보다 낮은 점수는 버리고, 많이 겹친 상자는 점수가 높은 것만 남긴다")
    void filtersAndSuppresses() {
        List<StrideOutput> outputs = emptyOutputs();
        outputs.getFirst().cls()[5] = 0.3f;
        outputs.getFirst().obj()[5] = 0.3f;
        assertTrue(YunetDecoder.decode(outputs, INPUT, 0.7).isEmpty());

        FaceDetection strong = new FaceDetection(0.95, 100, 100, 50, 50, new double[10]);
        FaceDetection weakSame = new FaceDetection(0.80, 105, 105, 50, 50, new double[10]);
        FaceDetection other = new FaceDetection(0.75, 300, 300, 50, 50, new double[10]);
        assertEquals(List.of(strong, other), YunetDecoder.suppressOverlaps(List.of(weakSame, other, strong), 0.3));
    }

    private static List<StrideOutput> emptyOutputs() {
        List<StrideOutput> outputs = new ArrayList<>();
        for (int stride : YunetDecoder.STRIDES) {
            int cells = (INPUT / stride) * (INPUT / stride);
            outputs.add(new StrideOutput(stride, new float[cells], new float[cells], new float[cells][4], new float[cells][10]));
        }
        return outputs;
    }
}
