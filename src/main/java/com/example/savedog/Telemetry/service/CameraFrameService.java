package com.example.savedog.Telemetry.service;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CameraFrameService {

    public void store(Long robotId, byte[] jpeg) {
        // TODO(F-04): 로보독별 최신 프레임 1장을 수신 시각과 함께 보관
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    public byte[] getLatest(Long robotId) {
        // TODO(F-04): 보관 중인 프레임 반환, 없으면 BusinessException(FRAME_NOT_FOUND)
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    public Optional<byte[]> findRecent(Long robotId) {
        // TODO(F-04): 안전도 판단용. 너무 오래된 프레임은 Optional.empty()
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }
}
