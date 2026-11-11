package com.example.savedog.Owner.face;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * YuNet(2023mar) 출력을 얼굴 상자로 바꾼다. 좌표는 모델 입력 사진(640×640) 기준 픽셀.
 * 간격(8·16·32)마다 칸별로 얼굴일 확률(cls·obj), 상자(bbox 4개), 특징점(kps 10개)이 나온다. OpenCV FaceDetectorYN과 같은 방식으로 푼다.
 *   점수 = √(cls × obj), 중심 = (칸 + bbox[0·1]) × 간격, 크기 = e^bbox[2·3] × 간격, 특징점 = (칸 + kps) × 간격
 */
final class YunetDecoder {
    static final int[] STRIDES = {8, 16, 32};

    private YunetDecoder() {
    }

    // 간격 하나의 출력. cls·obj는 [칸], bbox는 [칸][4], kps는 [칸][10]
    record StrideOutput(int stride, float[] cls, float[] obj, float[][] bbox, float[][] kps) {
    }

    static List<FaceDetection> decode(List<StrideOutput> outputs, int inputSize, double minScore) {
        List<FaceDetection> faces = new ArrayList<>();
        for (StrideOutput output : outputs) {
            int stride = output.stride();
            int columns = inputSize / stride;
            for (int cell = 0; cell < output.cls().length; cell++) {
                double score = Math.sqrt(clamp(output.cls()[cell]) * clamp(output.obj()[cell]));
                if (score < minScore) {
                    continue;
                }
                int row = cell / columns;
                int column = cell % columns;
                float[] box = output.bbox()[cell];
                double centerX = (column + box[0]) * stride;
                double centerY = (row + box[1]) * stride;
                double width = Math.exp(box[2]) * stride;
                double height = Math.exp(box[3]) * stride;
                if (!Double.isFinite(width) || !Double.isFinite(height)) {
                    continue;
                }
                double[] landmarks = new double[10];
                for (int n = 0; n < 5; n++) {
                    landmarks[2 * n] = (output.kps()[cell][2 * n] + column) * stride;
                    landmarks[2 * n + 1] = (output.kps()[cell][2 * n + 1] + row) * stride;
                }
                faces.add(new FaceDetection(score, centerX - width / 2, centerY - height / 2, width, height, landmarks));
            }
        }
        return faces;
    }

    // 많이 겹친 상자 중 점수가 높은 것만 남긴다.
    static List<FaceDetection> suppressOverlaps(List<FaceDetection> faces, double maxOverlap) {
        List<FaceDetection> sorted = new ArrayList<>(faces);
        sorted.sort(Comparator.comparingDouble(FaceDetection::score).reversed());
        List<FaceDetection> kept = new ArrayList<>();
        for (FaceDetection face : sorted) {
            if (kept.stream().noneMatch(other -> overlap(other, face) > maxOverlap)) {
                kept.add(face);
            }
        }
        return kept;
    }

    static double overlap(FaceDetection a, FaceDetection b) {
        double width = Math.min(a.x() + a.width(), b.x() + b.width()) - Math.max(a.x(), b.x());
        double height = Math.min(a.y() + a.height(), b.y() + b.height()) - Math.max(a.y(), b.y());
        if (width <= 0 || height <= 0) {
            return 0;
        }
        double intersection = width * height;
        return intersection / (a.width() * a.height() + b.width() * b.height() - intersection);
    }

    private static double clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }
}
