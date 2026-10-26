package com.example.savedog.Vision.detector;

// 사진에서 찾은 물체 하나. 좌표는 원본 사진 기준 0~1 비율이고 왼쪽 위가 (0, 0)이다.
// label은 모델의 영어 이름(person), name은 화면·신고에 쓰는 한국어 이름(사람)
public record Detection(
        String label,
        String name,
        double confidence,
        double left,
        double top,
        double right,
        double bottom
) {
    public double width() {
        return right - left;
    }

    public double height() {
        return bottom - top;
    }
}
