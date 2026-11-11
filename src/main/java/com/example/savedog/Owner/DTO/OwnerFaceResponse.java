package com.example.savedog.Owner.DTO;

import java.time.LocalDateTime;

/**
 * 얼굴 등록 결과.
 * faceScore: 사진에서 얼굴을 찾은 확신 정도(0~1)
 * similarityToOthers: 이미 등록된 얼굴들과의 평균 유사도. 같은 사람이면 0.363 이상이 나온다. (처음 등록이면 null)
 * registeredCount: 지금까지 등록된 얼굴 수
 */
public record OwnerFaceResponse(
        Long id,
        double faceScore,
        Double similarityToOthers,
        long registeredCount,
        LocalDateTime createdAt
) {
}
