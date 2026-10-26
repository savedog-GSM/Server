package com.example.savedog.Vision.detector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * YOLO 모델의 출력 숫자 묶음을 물체 상자로 바꾼다. 상자 좌표는 모델 입력 사진(정사각형) 기준 픽셀이다.
 * 공개 모델마다 출력 모양이 달라서 세 가지를 모양으로 구분한다. (S = 입력 한 변, 칸 수 = (S/8)² + (S/16)² + (S/32)²)
 * - YOLOX:  [1, 칸 수, 5 + 종류 수]      칸 기준 값이라 직접 풀어야 한다. 물체일 확률이 따로 있다.
 * - YOLOv5: [1, 칸 수 × 3, 5 + 종류 수]  이미 픽셀 값이다. 물체일 확률이 따로 있다.
 * - YOLOv8: [1, 4 + 종류 수, 칸 수]      이미 픽셀 값이다. 행·열이 뒤집혀 있고 물체일 확률이 없다.
 */
final class YoloDecoder {
    private static final int[] STRIDES = {8, 16, 32};
    private static final double SAME_PLACE = 0.8;

    enum Format { YOLOX, YOLOV5, YOLOV8 }

    // 모델 입력 사진 기준의 상자 하나
    record Candidate(int classIndex, float score, float x1, float y1, float x2, float y2) {
        float area() {
            return Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        }
    }

    private YoloDecoder() {
    }

    static Format formatOf(long[] outputShape, int inputSize, int classCount) {
        if (outputShape.length != 3 || outputShape[0] != 1) {
            throw unsupported(outputShape);
        }
        long cells = cellCount(inputSize);
        if (outputShape[1] == cells && outputShape[2] == 5 + classCount) {
            return Format.YOLOX;
        }
        if (outputShape[1] == cells * 3 && outputShape[2] == 5 + classCount) {
            return Format.YOLOV5;
        }
        if (outputShape[1] == 4 + classCount && outputShape[2] == cells) {
            return Format.YOLOV8;
        }
        throw unsupported(outputShape);
    }

    // rows: [칸][5 + 종류 수]
    static List<Candidate> decodeYolox(float[][] rows, int inputSize, double minScore) {
        List<Candidate> candidates = new ArrayList<>();
        int row = 0;
        for (int stride : STRIDES) {
            int cellsPerSide = inputSize / stride;
            for (int gridY = 0; gridY < cellsPerSide; gridY++) {
                for (int gridX = 0; gridX < cellsPerSide; gridX++, row++) {
                    float[] values = rows[row];
                    float centerX = (values[0] + gridX) * stride;
                    float centerY = (values[1] + gridY) * stride;
                    float width = (float) Math.exp(values[2]) * stride;
                    float height = (float) Math.exp(values[3]) * stride;
                    addBest(candidates, values, 5, values[4], centerX, centerY, width, height, minScore);
                }
            }
        }
        return candidates;
    }

    // rows: [칸 × 3][5 + 종류 수]
    static List<Candidate> decodeYolov5(float[][] rows, double minScore) {
        List<Candidate> candidates = new ArrayList<>();
        for (float[] values : rows) {
            addBest(candidates, values, 5, values[4], values[0], values[1], values[2], values[3], minScore);
        }
        return candidates;
    }

    // channels: [4 + 종류 수][칸]
    static List<Candidate> decodeYolov8(float[][] channels, double minScore) {
        List<Candidate> candidates = new ArrayList<>();
        int classCount = channels.length - 4;
        float[] values = new float[channels.length];
        for (int cell = 0; cell < channels[0].length; cell++) {
            for (int channel = 0; channel < channels.length; channel++) {
                values[channel] = channels[channel][cell];
            }
            if (classCount > 0) {
                addBest(candidates, values, 4, 1f, values[0], values[1], values[2], values[3], minScore);
            }
        }
        return candidates;
    }

    /**
     * 같은 물체에 겹쳐 찍힌 상자 중 가장 확실한 것만 남긴다.
     * 점수가 높은 순서로 남기고, 이미 남긴 상자와 같은 종류로 maxOverlap보다 많이 겹치면 버린다.
     * 종류가 달라도 거의 같은 자리(SAME_PLACE 이상)면 한 물체를 두 이름으로 본 것이므로 버린다. (개·고양이 등)
     */
    static List<Candidate> suppressOverlaps(List<Candidate> candidates, double maxOverlap) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(Candidate::score).reversed());
        List<Candidate> kept = new ArrayList<>();
        for (Candidate candidate : sorted) {
            boolean duplicate = kept.stream().anyMatch(other -> {
                double overlap = overlap(other, candidate);
                return overlap > SAME_PLACE || (other.classIndex() == candidate.classIndex() && overlap > maxOverlap);
            });
            if (!duplicate) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    // 두 상자가 겹친 넓이 ÷ 둘을 합친 넓이 (IoU)
    static double overlap(Candidate a, Candidate b) {
        float width = Math.min(a.x2(), b.x2()) - Math.max(a.x1(), b.x1());
        float height = Math.min(a.y2(), b.y2()) - Math.max(a.y1(), b.y1());
        if (width <= 0 || height <= 0) {
            return 0;
        }
        double intersection = (double) width * height;
        double union = a.area() + b.area() - intersection;
        return union <= 0 ? 0 : intersection / union;
    }

    private static void addBest(
            List<Candidate> candidates, float[] values, int firstClass, float objectness,
            float centerX, float centerY, float width, float height, double minScore
    ) {
        int bestClass = -1;
        float bestScore = 0;
        for (int i = firstClass; i < values.length; i++) {
            float score = objectness * values[i];
            if (score > bestScore) {
                bestScore = score;
                bestClass = i - firstClass;
            }
        }
        // 값이 깨져서 NaN·무한대가 나온 상자는 버린다.
        if (bestClass < 0 || bestScore < minScore || !Float.isFinite(width) || !Float.isFinite(height)
                || !Float.isFinite(centerX) || !Float.isFinite(centerY)) {
            return;
        }
        candidates.add(new Candidate(
                bestClass, bestScore,
                centerX - width / 2, centerY - height / 2,
                centerX + width / 2, centerY + height / 2
        ));
    }

    private static long cellCount(int inputSize) {
        long cells = 0;
        for (int stride : STRIDES) {
            long perSide = inputSize / stride;
            cells += perSide * perSide;
        }
        return cells;
    }

    private static IllegalArgumentException unsupported(long[] shape) {
        return new IllegalArgumentException("지원하지 않는 모델 출력 모양입니다: " + Arrays.toString(shape)
                + " (YOLOX, YOLOv5, YOLOv8의 COCO 80종 모델만 읽을 수 있습니다)");
    }
}
