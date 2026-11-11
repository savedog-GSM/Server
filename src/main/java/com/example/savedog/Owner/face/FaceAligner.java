package com.example.savedog.Owner.face;

import java.awt.image.BufferedImage;

/**
 * 눈·코·입 위치 5곳이 정해진 자리에 오도록 얼굴을 돌리고 크기를 맞춰 112×112로 잘라 낸다. (SFace 입력)
 * 회전·확대·이동만 쓰는 변환(닮음 변환)을 최소제곱으로 구한다. OpenCV FaceRecognizerSF.alignCrop과 같은 기준 자리를 쓴다.
 */
final class FaceAligner {
    static final int SIZE = 112;
    // 112×112 안에서 오른쪽 눈, 왼쪽 눈, 코끝, 오른쪽 입꼬리, 왼쪽 입꼬리가 올 자리
    static final double[] TEMPLATE = {
            38.2946, 51.6963,
            73.5318, 51.5014,
            56.0252, 71.7366,
            41.5493, 92.3655,
            70.7299, 92.2041
    };

    private FaceAligner() {
    }

    // landmarks(원본 좌표 10개)를 TEMPLATE로 옮기는 변환 [a, b, tx, ty]. 원본 (x, y) → (a·x − b·y + tx, b·x + a·y + ty)
    static double[] similarityTransform(double[] landmarks) {
        int points = TEMPLATE.length / 2;
        double sourceX = 0, sourceY = 0, targetX = 0, targetY = 0;
        for (int i = 0; i < points; i++) {
            sourceX += landmarks[2 * i];
            sourceY += landmarks[2 * i + 1];
            targetX += TEMPLATE[2 * i];
            targetY += TEMPLATE[2 * i + 1];
        }
        sourceX /= points;
        sourceY /= points;
        targetX /= points;
        targetY /= points;

        double dotSum = 0, crossSum = 0, norm = 0;
        for (int i = 0; i < points; i++) {
            double x = landmarks[2 * i] - sourceX;
            double y = landmarks[2 * i + 1] - sourceY;
            double u = TEMPLATE[2 * i] - targetX;
            double v = TEMPLATE[2 * i + 1] - targetY;
            dotSum += x * u + y * v;
            crossSum += x * v - y * u;
            norm += x * x + y * y;
        }
        if (norm == 0) {
            throw new IllegalArgumentException("얼굴 특징점이 한 점에 모여 있어 정렬할 수 없습니다.");
        }
        double a = dotSum / norm;
        double b = crossSum / norm;
        double tx = targetX - (a * sourceX - b * sourceY);
        double ty = targetY - (b * sourceX + a * sourceY);
        return new double[]{a, b, tx, ty};
    }

    // 112×112 얼굴 사진. 바깥은 검은색으로 채운다.
    static BufferedImage align(BufferedImage image, double[] landmarks) {
        double[] transform = similarityTransform(landmarks);
        double a = transform[0], b = transform[1], tx = transform[2], ty = transform[3];
        double determinant = a * a + b * b;
        BufferedImage aligned = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        for (int v = 0; v < SIZE; v++) {
            for (int u = 0; u < SIZE; u++) {
                // 결과 칸에서 거꾸로 원본 위치를 구해 가져온다.
                double du = u - tx;
                double dv = v - ty;
                double x = (a * du + b * dv) / determinant;
                double y = (-b * du + a * dv) / determinant;
                aligned.setRGB(u, v, sample(image, x, y));
            }
        }
        return aligned;
    }

    // 주변 4픽셀을 거리에 따라 섞는다. (쌍선형)
    private static int sample(BufferedImage image, double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        double red = 0, green = 0, blue = 0;
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = 0; dx <= 1; dx++) {
                double weight = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy);
                int px = x0 + dx;
                int py = y0 + dy;
                if (weight == 0 || px < 0 || py < 0 || px >= image.getWidth() || py >= image.getHeight()) {
                    continue;
                }
                int rgb = image.getRGB(px, py);
                red += ((rgb >> 16) & 0xFF) * weight;
                green += ((rgb >> 8) & 0xFF) * weight;
                blue += (rgb & 0xFF) * weight;
            }
        }
        return (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    private static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }
}
