package com.example.savedog.Vision.detector;

import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.example.savedog.Vision.detector.YoloDecoder.Candidate;
import com.example.savedog.Vision.detector.YoloDecoder.Format;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * ONNX로 내보낸 YOLO 모델(COCO 80종)을 서버 안에서 돌린다.
 * 사진은 비율을 유지한 채 모델 입력 크기의 정사각형 왼쪽 위에 맞춰 넣고 남는 곳은 회색(114)으로 채운다.
 */
public class OnnxYoloDetector implements ObjectDetector {
    // 이보다 확실하지 않은 상자는 버린다. 위험 판단은 PathObstacleRule에서 더 높은 기준으로 다시 거른다.
    private static final double MIN_SCORE = 0.3;
    private static final double MAX_OVERLAP = 0.45;
    private static final int MAX_DETECTIONS = 50;
    private static final int PADDING_GRAY = 114;
    // 학교 서버를 다른 서비스와 같이 쓰므로 계산 스레드를 제한한다.
    private static final int THREADS = 2;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;
    private final int inputSize;
    private final Format format;

    public OnnxYoloDetector(Path modelFile) throws OrtException {
        environment = OrtEnvironment.getEnvironment();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(THREADS);
            session = environment.createSession(modelFile.toString(), options);
        }
        try {
            Map.Entry<String, NodeInfo> input = session.getInputInfo().entrySet().iterator().next();
            inputName = input.getKey();
            inputSize = squareInputSize(((TensorInfo) input.getValue().getInfo()).getShape());
            long[] outputShape = ((TensorInfo) session.getOutputInfo().values().iterator().next().getInfo()).getShape();
            format = YoloDecoder.formatOf(outputShape, inputSize, CocoLabels.COUNT);
        } catch (RuntimeException e) {
            session.close();
            throw e;
        }
    }

    public String description() {
        return "%s, 입력 %dx%d".formatted(format, inputSize, inputSize);
    }

    @Override
    public List<Detection> detect(BufferedImage image) {
        double scale = Math.min((double) inputSize / image.getWidth(), (double) inputSize / image.getHeight());
        float[] pixels = toInput(image, scale);
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                environment, FloatBuffer.wrap(pixels), new long[]{1, 3, inputSize, inputSize});
             OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            float[][] output = ((float[][][]) result.get(0).getValue())[0];
            List<Candidate> candidates = switch (format) {
                case YOLOX -> YoloDecoder.decodeYolox(output, inputSize, MIN_SCORE);
                case YOLOV5 -> YoloDecoder.decodeYolov5(output, MIN_SCORE);
                case YOLOV8 -> YoloDecoder.decodeYolov8(output, MIN_SCORE);
            };
            return YoloDecoder.suppressOverlaps(candidates, MAX_OVERLAP).stream()
                    .limit(MAX_DETECTIONS)
                    .map(candidate -> toDetection(candidate, scale, image.getWidth(), image.getHeight()))
                    .toList();
        } catch (OrtException e) {
            throw new IllegalStateException("물체 인식 모델 실행 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            // 서버를 끄는 중이라 더 할 수 있는 일이 없다.
        }
    }

    // [1, 3, S, S] 순서의 숫자 배열. YOLOX는 BGR 0~255, YOLOv5·v8은 RGB 0~1을 받는다.
    private float[] toInput(BufferedImage image, double scale) {
        BufferedImage canvas = new BufferedImage(inputSize, inputSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setColor(new Color(PADDING_GRAY, PADDING_GRAY, PADDING_GRAY));
            graphics.fillRect(0, 0, inputSize, inputSize);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0,
                    Math.max(1, (int) Math.round(image.getWidth() * scale)),
                    Math.max(1, (int) Math.round(image.getHeight() * scale)), null);
        } finally {
            graphics.dispose();
        }

        int area = inputSize * inputSize;
        int[] rgb = canvas.getRGB(0, 0, inputSize, inputSize, null, 0, inputSize);
        float[] pixels = new float[3 * area];
        boolean bgr = format == Format.YOLOX;
        float divisor = bgr ? 1f : 255f;
        for (int i = 0; i < area; i++) {
            float red = ((rgb[i] >> 16) & 0xFF) / divisor;
            float green = ((rgb[i] >> 8) & 0xFF) / divisor;
            float blue = (rgb[i] & 0xFF) / divisor;
            pixels[i] = bgr ? blue : red;
            pixels[area + i] = green;
            pixels[2 * area + i] = bgr ? red : blue;
        }
        return pixels;
    }

    private Detection toDetection(Candidate candidate, double scale, int width, int height) {
        return new Detection(
                CocoLabels.ENGLISH[candidate.classIndex()],
                CocoLabels.KOREAN[candidate.classIndex()],
                candidate.score(),
                ratio(candidate.x1() / scale, width),
                ratio(candidate.y1() / scale, height),
                ratio(candidate.x2() / scale, width),
                ratio(candidate.y2() / scale, height)
        );
    }

    private static double ratio(double pixel, int length) {
        return Math.clamp(pixel / length, 0.0, 1.0);
    }

    private static int squareInputSize(long[] shape) {
        if (shape.length != 4 || shape[1] != 3 || shape[2] <= 0 || shape[2] != shape[3]) {
            throw new IllegalArgumentException("모델 입력은 [1, 3, S, S] 모양이어야 합니다.");
        }
        return (int) shape[2];
    }
}
