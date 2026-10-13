package com.example.savedog.Telemetry.service;

import com.example.savedog.Telemetry.DTO.LocationRequest;
import com.example.savedog.Telemetry.domain.RobotLocation;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class LocationService {

    public void store(LocationRequest request) {
        // TODO(F-15): 최신 위치 하나를 받은 시각과 함께 보관
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    public Optional<RobotLocation> findRecent() {
        // TODO(F-15): 받은 지 5초 이내이고 오차 반경이 30m 이하인 위치만 반환
        return Optional.empty();
    }
}
