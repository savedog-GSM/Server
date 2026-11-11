package com.example.savedog.Owner.service;

import java.time.LocalDateTime;

/**
 * 카메라 사진 한 장에서 주인을 찾아본 결과.
 * facesInFrame: 사진 속 얼굴 수, ownerVisible: 그중 등록된 주인과 같은 사람(유사도 0.363 이상)이 있는지
 * 아래 값은 주인과 가장 닮은 얼굴 기준이다. 얼굴이 없으면 null
 *   similarity: 등록된 얼굴들 중 가장 높은 유사도
 *   horizontalOffset: 화면 가로 위치. -1(왼쪽 끝) ~ 0(가운데) ~ 1(오른쪽 끝)
 *   verticalOffset: 화면 세로 위치. -1(위 끝) ~ 1(아래 끝)
 *   direction: LEFT·CENTER·RIGHT (가운데 띠는 가로 ±0.2)
 *   faceWidthRatio: 얼굴 너비 ÷ 사진 너비. 클수록 가깝다. (대략 0.2 이상이면 1m 안팎)
 */
public record OwnerSighting(
        LocalDateTime frameReceivedAt,
        LocalDateTime analyzedAt,
        int facesInFrame,
        boolean ownerVisible,
        Double similarity,
        Double horizontalOffset,
        Double verticalOffset,
        Direction direction,
        Double faceWidthRatio
) {
    public enum Direction { LEFT, CENTER, RIGHT }
}
