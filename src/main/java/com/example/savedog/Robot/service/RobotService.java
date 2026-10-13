package com.example.savedog.Robot.service;

import com.example.savedog.Robot.DTO.RobotCommandRequest;
import com.example.savedog.Robot.DTO.RobotCommandResponse;
import com.example.savedog.Robot.DTO.RobotStatusResponse;
import com.example.savedog.Robot.websocket.RobotCommandSender;
import com.example.savedog.Robot.websocket.RobotSessionRegistry;
import com.example.savedog.Safety.repository.SafetyRecordRepository;
import com.example.savedog.Telemetry.repository.TelemetryRepository;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotService {
    private final RobotSessionRegistry sessionRegistry;
    private final RobotCommandSender commandSender;
    private final TelemetryRepository telemetryRepository;
    private final SafetyRecordRepository safetyRecordRepository;

    @Transactional(readOnly = true)
    public RobotStatusResponse getStatus(Long robotId) {
        // TODO(F-01): sessionRegistry.isConnected(robotId) + 최근 텔레메트리/안전도 기록으로 응답 구성
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    public RobotCommandResponse sendCommand(Long robotId, RobotCommandRequest request) {
        // TODO(F-08): commandId 채번 → RobotCommandMessage 생성 → commandSender.send(robotId, message)
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    public void emergencyStop(Long robotId) {
        // TODO(F-09): STOP 명령 전송. 로보독이 끊겨 있어도 호출한 쪽(안전도 판단)이 실패하지 않게 처리
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }

    public void handleAck(Long robotId, String payload) {
        // TODO(F-08): payload(JSON)를 RobotAckMessage로 파싱해 명령 결과 처리
        log.info("로보독 {} 수신: {}", robotId, payload);
    }
}
