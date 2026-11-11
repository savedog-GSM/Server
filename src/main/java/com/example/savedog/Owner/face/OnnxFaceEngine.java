package com.example.savedog.Owner.face;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;
import com.example.savedog.Owner.face.YunetDecoder.StrideOutput;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenCV 모델 저장소의 YuNet(얼굴 찾기, MIT)과 SFace(얼굴 특징값, Apache-2.0)를 서버 안에서 돌린다.
 * 둘 다 BGR 순서의 0~255 값을 받는다. SFace만 RGB로 바꿔 넣는다. (OpenCV FaceRecognizerSF와 같다)
 */
public class OnnxFaceEngine implements FaceEngine, AutoCloseable {
    private static final int DETECTOR_SIZE = 640;
    // OpenCV 기본값은 0.9인데, 살짝 돌리거나 작게 찍힌 얼굴이 0.9 아래로 떨어져(실제 사진으로 확인) 낮춘다.
    private static final double MIN_FACE_SCORE = 0.7;
    private static final double MAX_OVERLAP = 0.3;
    private static final int THREADS = 2;

    private final OrtEnvironment environment;
    private final OrtSession detector;
    private final OrtSession recognizer;

    public OnnxFaceEngine(Path detectorModel, Path recognizerModel) throws OrtException {
        environment = OrtEnvironment.getEnvironment();
        detector = open(detectorModel);
        try {
            recognizer = open(recognizerModel);
        } catch (OrtException | RuntimeException e) {
            detector.close();
            throw e;
        }
    }

    private OrtSession open(Path model) throws OrtException {
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(THREADS);
            // 모델 파일 형식에 대한 경고가 시작할 때마다 길게 찍히므로 오류만 남긴다.
            options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
            return environment.createSession(model.toString(), options);
        }
    }

    @Override
    public List<FaceDetection> detect(BufferedImage image) {
        double scale = Math.min((double) DETECTOR_SIZE / image.getWidth(), (double) DETECTOR_SIZE / image.getHeight());
        BufferedImage canvas = new BufferedImage(DETECTOR_SIZE, DETECTOR_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0,
                    Math.max(1, (int) Math.round(image.getWidth() * scale)),
                    Math.max(1, (int) Math.round(image.getHeight() * scale)), null);
        } finally {
            graphics.dispose();
        }
        try (OnnxTensor input = tensor(canvas, false);
             OrtSession.Result result = detector.run(Map.of("input", input))) {
            List<StrideOutput> outputs = new ArrayList<>();
            for (int stride : YunetDecoder.STRIDES) {
                outputs.add(new StrideOutput(
                        stride,
                        column(result, "cls_" + stride),
                        column(result, "obj_" + stride),
                        rows(result, "bbox_" + stride),
                        rows(result, "kps_" + stride)
                ));
            }
            return YunetDecoder.suppressOverlaps(YunetDecoder.decode(outputs, DETECTOR_SIZE, MIN_FACE_SCORE), MAX_OVERLAP)
                    .stream()
                    .map(face -> toOriginal(face, scale))
                    .toList();
        } catch (OrtException e) {
            throw new IllegalStateException("얼굴 찾기 모델 실행 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public float[] embed(BufferedImage image, FaceDetection face) {
        BufferedImage aligned = FaceAligner.align(image, face.landmarks());
        try (OnnxTensor input = tensor(aligned, true);
             OrtSession.Result result = recognizer.run(Map.of("data", input))) {
            float[] feature = ((float[][]) result.get(0).getValue())[0];
            return normalize(feature);
        } catch (OrtException e) {
            throw new IllegalStateException("얼굴 특징값 모델 실행 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            detector.close();
            recognizer.close();
        } catch (OrtException e) {
            // 서버를 끄는 중이라 더 할 수 있는 일이 없다.
        }
    }

    // [1, 3, H, W]. rgb=false면 BGR 순서
    private OnnxTensor tensor(BufferedImage image, boolean rgb) throws OrtException {
        int width = image.getWidth();
        int height = image.getHeight();
        int area = width * height;
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        float[] values = new float[3 * area];
        for (int i = 0; i < area; i++) {
            float red = (pixels[i] >> 16) & 0xFF;
            float green = (pixels[i] >> 8) & 0xFF;
            float blue = pixels[i] & 0xFF;
            values[i] = rgb ? red : blue;
            values[area + i] = green;
            values[2 * area + i] = rgb ? blue : red;
        }
        return OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), new long[]{1, 3, height, width});
    }

    private static float[] column(OrtSession.Result result, String name) throws OrtException {
        float[][] values = ((float[][][]) result.get(name).orElseThrow().getValue())[0];
        float[] column = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            column[i] = values[i][0];
        }
        return column;
    }

    private static float[][] rows(OrtSession.Result result, String name) throws OrtException {
        return ((float[][][]) result.get(name).orElseThrow().getValue())[0];
    }

    private static FaceDetection toOriginal(FaceDetection face, double scale) {
        double[] landmarks = face.landmarks().clone();
        for (int i = 0; i < landmarks.length; i++) {
            landmarks[i] /= scale;
        }
        return new FaceDetection(face.score(), face.x() / scale, face.y() / scale,
                face.width() / scale, face.height() / scale, landmarks);
    }

    private static float[] normalize(float[] feature) {
        double length = 0;
        for (float value : feature) {
            length += value * value;
        }
        length = Math.sqrt(length);
        if (length == 0 || !Double.isFinite(length)) {
            throw new IllegalStateException("얼굴 특징값이 비어 있습니다.");
        }
        float[] normalized = new float[feature.length];
        for (int i = 0; i < feature.length; i++) {
            normalized[i] = (float) (feature[i] / length);
        }
        return normalized;
    }
}
