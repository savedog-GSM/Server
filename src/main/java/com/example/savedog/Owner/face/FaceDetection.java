package com.example.savedog.Owner.face;

// 사진에서 찾은 얼굴 하나. 좌표는 원본 사진의 픽셀.
// landmarks: 오른쪽 눈, 왼쪽 눈, 코끝, 오른쪽 입꼬리, 왼쪽 입꼬리 순서의 (x, y) 10개 (사진 기준 왼쪽·오른쪽이 아니라 그 사람 기준)
public record FaceDetection(
        double score,
        double x,
        double y,
        double width,
        double height,
        double[] landmarks
) {
}
