package com.example.savedog.Owner.DTO;

// 지운 얼굴 수와 남은 얼굴 수
public record OwnerFaceDeleteResponse(
        long deleted,
        long remaining
) {
}
