package com.example.savedog.Owner.face;

import java.awt.image.BufferedImage;
import java.util.List;

// 얼굴 모델 파일이 없거나 열지 못했을 때 쓰인다. 서버는 그대로 뜨고 얼굴 등록만 503으로 응답한다.
public class UnconfiguredFaceEngine implements FaceEngine {

    @Override
    public List<FaceDetection> detect(BufferedImage image) {
        throw new IllegalStateException("얼굴 모델이 설정되지 않았습니다.");
    }

    @Override
    public float[] embed(BufferedImage image, FaceDetection face) {
        throw new IllegalStateException("얼굴 모델이 설정되지 않았습니다.");
    }

    @Override
    public boolean available() {
        return false;
    }
}
