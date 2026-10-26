package com.example.savedog.Vision.detector;

import java.awt.image.BufferedImage;
import java.util.List;

// 모델 파일이 없거나 열지 못했을 때 쓰인다. 서버는 그대로 뜨고 위험도는 거리만으로 판단한다.
public class UnconfiguredObjectDetector implements ObjectDetector {

    @Override
    public List<Detection> detect(BufferedImage image) {
        throw new IllegalStateException("물체 인식 모델이 설정되지 않았습니다.");
    }

    @Override
    public boolean available() {
        return false;
    }
}
