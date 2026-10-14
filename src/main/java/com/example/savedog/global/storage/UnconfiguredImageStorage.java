package com.example.savedog.global.storage;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;

import java.net.URI;
import java.time.Duration;

// S3 버킷을 설정하지 않았을 때 쓰인다. 서버는 정상적으로 뜨고 이미지 기능만 503으로 응답한다.
public class UnconfiguredImageStorage implements ImageStorage {

    @Override
    public void put(String key, byte[] content, String contentType) {
        throw unavailable();
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        throw unavailable();
    }

    @Override
    public boolean exists(String key) {
        throw unavailable();
    }

    @Override
    public URI presignedUrl(String key, Duration validFor) {
        throw unavailable();
    }

    private BusinessException unavailable() {
        return new BusinessException(
                ErrorCode.IMAGE_STORAGE_UNAVAILABLE,
                "이미지 저장소(S3)가 설정되지 않았습니다. S3_BUCKET 환경 변수를 설정해 주세요."
        );
    }
}
