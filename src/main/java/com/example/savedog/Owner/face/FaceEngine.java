package com.example.savedog.Owner.face;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 얼굴 찾기(YuNet)와 얼굴 특징값 뽑기(SFace).
 * 특징값은 길이 1로 맞춘 숫자 128개라, 두 값의 내적(코사인 유사도)이 클수록 같은 사람이다.
 * 모델 파일이 없으면 available()이 false인 구현체가 쓰인다.
 */
public interface FaceEngine {
    // 같은 사람으로 볼 코사인 유사도 기준 (SFace 권장값)
    double SAME_PERSON_SIMILARITY = 0.363;

    List<FaceDetection> detect(BufferedImage image);

    float[] embed(BufferedImage image, FaceDetection face);

    default boolean available() {
        return true;
    }

    static double similarity(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }
}
