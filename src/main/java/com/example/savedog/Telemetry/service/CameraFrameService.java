package com.example.savedog.Telemetry.service;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

// 프레임 이미지는 S3에 둔다. 최신 1장을 같은 키에 덮어쓴다.
@Service
public class CameraFrameService {

    public void store(byte[] jpeg) {
        // TODO(F-03): S3에 올리고(최신 프레임 키에 덮어쓰기) 받은 시각을 기억
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    public URI getLatestUrl() {
        // TODO(F-03): 최신 프레임의 S3 presigned URL 발급, 프레임이 없으면 BusinessException(FRAME_NOT_FOUND)
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    public Optional<String> findRecentKey() {
        // TODO(F-03): 신고 사진용. 받은 지 10초 이내인 최신 프레임의 S3 키, 없으면 Optional.empty()
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }
}
