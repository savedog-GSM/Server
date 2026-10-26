package com.example.savedog.Vision.detector;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 사진 한 장에서 물체를 찾는다. 실패하면 RuntimeException을 던진다.
 * 모델 파일이 없으면 available()이 false인 구현체(UnconfiguredObjectDetector)가 쓰인다.
 */
public interface ObjectDetector extends AutoCloseable {
    List<Detection> detect(BufferedImage image);

    default boolean available() {
        return true;
    }

    @Override
    default void close() {
    }
}
