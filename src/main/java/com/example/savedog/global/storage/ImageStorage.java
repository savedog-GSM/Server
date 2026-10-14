package com.example.savedog.global.storage;

import java.net.URI;
import java.time.Duration;

/**
 * 이미지 파일 보관소. 실패하면 BusinessException(IMAGE_STORAGE_UNAVAILABLE)을 던진다.
 * key는 보관소 안에서의 경로다. 예: frames/latest.jpg
 */
public interface ImageStorage {
    void put(String key, byte[] content, String contentType);

    void copy(String sourceKey, String targetKey);

    boolean exists(String key);

    // 정해진 시간 동안만 열리는 조회 주소
    URI presignedUrl(String key, Duration validFor);
}
