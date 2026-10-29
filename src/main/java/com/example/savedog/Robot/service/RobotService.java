package com.example.savedog.Robot.service;

import com.example.savedog.Robot.DTO.RobotAckMessage;
import com.example.savedog.Robot.DTO.RobotCommandMessage;
import com.example.savedog.Robot.DTO.RobotCommandRequest;
import com.example.savedog.Robot.DTO.RobotCommandResponse;
import com.example.savedog.Robot.DTO.RobotStatusResponse;
import com.example.savedog.Robot.domain.CommandType;
import com.example.savedog.Robot.websocket.RobotCommandSender;
import com.example.savedog.Robot.websocket.RobotSessionRegistry;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
import com.example.savedog.Telemetry.repository.TelemetryRepository;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.logging.LogSanitizer;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotService {
    private static final Set<CommandType> MOVES = Set.of(
            CommandType.FORWARD, CommandType.BACKWARD, CommandType.TURN_LEFT, CommandType.TURN_RIGHT
    );
    private static final Set<CommandType> TURNS = Set.of(CommandType.TURN_LEFT, CommandType.TURN_RIGHT);
    private static final int MAX_SPEED_PERCENT = 100;
    private static final int MAX_DURATION_MS = 60_000;
    private static final int MAX_TURN_DEGREES = 360;
    private static final int DEFAULT_BARK_DURATION_MS = 1_000;
    private static final int REMEMBERED_COMMANDS = 100;

    private final RobotSessionRegistry sessionRegistry;
    private final RobotCommandSender commandSender;
    private final TelemetryRepository telemetryRepository;
    private final LocationService locationService;
    private final JsonMapper jsonMapper;

    private final AtomicLong commandSequence = new AtomicLong();
    // 로보독이 결과를 돌려줄 때 어떤 명령이었는지 알 수 있게 최근 명령만 기억한다.
    private final Map<Long, CommandType> recentCommands = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, CommandType> eldest) {
            return size() > REMEMBERED_COMMANDS;
        }
    });

    @Transactional(readOnly = true)
    public RobotStatusResponse getStatus() {
        boolean connected = sessionRegistry.isConnected();
        var location = locationService.latest().orElse(null);
        LocalDateTime lastLocationAt = location == null ? null : location.receivedAt();
        return telemetryRepository.findFirstByOrderByRecordedAtDesc()
                .map(TelemetryResponse::from)
                .map(latest -> new RobotStatusResponse(
                        connected,
                        latest.recordedAt(),
                        lastLocationAt,
                        location == null ? latest.latitude() : Double.valueOf(location.latitude()),
                        location == null ? latest.longitude() : Double.valueOf(location.longitude()),
                        latest.sensor(),
                        latest.risk()
                ))
                .orElseGet(() -> new RobotStatusResponse(
                        connected,
                        null,
                        lastLocationAt,
                        location == null ? null : location.latitude(),
                        location == null ? null : location.longitude(),
                        null,
                        null
                ));
    }

    public RobotCommandResponse sendCommand(RobotCommandRequest request) {
        RequestValidator.required(request, "요청 본문");
        CommandType type = RequestValidator.required(request.type(), "type");
        boolean move = MOVES.contains(type);

        // 속도는 이동 명령과 재개(전진)에만, 시간은 이동과 짖기에만, 각도는 회전에만 의미가 있다.
        // 그 밖의 명령에 딸려 온 값은 버린다.
        Integer speed = null;
        if ((move || type == CommandType.RESUME) && request.speed() != null) {
            speed = RequestValidator.inRange(request.speed(), 0, MAX_SPEED_PERCENT, "speed");
        }
        Integer durationMs = null;
        if ((move || type == CommandType.BARK) && request.durationMs() != null) {
            durationMs = RequestValidator.inRange(request.durationMs(), 1, MAX_DURATION_MS, "durationMs");
        }
        if (type == CommandType.BARK && durationMs == null) {
            durationMs = DEFAULT_BARK_DURATION_MS;
        }
        Integer degrees = null;
        if (TURNS.contains(type) && request.degrees() != null) {
            degrees = RequestValidator.inRange(request.degrees(), 1, MAX_TURN_DEGREES, "degrees");
            // 각도만큼 돌고 멈추는 것과 시간만큼 도는 것은 함께 정할 수 없다.
            if (durationMs != null) {
                throw RequestValidator.invalid("회전에는 degrees와 durationMs 중 하나만 보낼 수 있습니다.");
            }
        }

        RobotCommandMessage message = dispatch(type, speed, durationMs, degrees);
        return new RobotCommandResponse(message.commandId(), type, LocalDateTime.now());
    }

    // 위험 감지 시 관제자 조작 없이 멈추고, 앉은 다음, 짖게 한다. 보냈으면 true.
    // 로보독은 받은 순서대로 하나씩 실행한다. 앉는 데 2초쯤 걸리므로 짖기는 앉은 뒤에 나온다.
    // 로보독이 끊겨 있어도 호출한 쪽(센서 값 처리)이 실패하지 않도록 예외를 밖으로 내보내지 않는다.
    public boolean emergencyStop() {
        try {
            dispatch(CommandType.STOP, null, null);
            dispatch(CommandType.SIT, null, null);
            dispatch(CommandType.BARK, null, DEFAULT_BARK_DURATION_MS);
            log.warn("위험 감지: 로보독에 멈추기, 앉기, 짖기를 보냈습니다.");
            return true;
        } catch (BusinessException e) {
            log.warn("위험 감지: 로보독에 멈춤 신호를 보내지 못했습니다. ({})", e.getMessage());
            return false;
        }
    }

    // 로보독이 보낸 처리 결과. 형식이 틀려도 연결을 끊지 않고 기록만 남긴다.
    public void handleAck(String payload) {
        RobotAckMessage ack;
        try {
            ack = jsonMapper.readValue(payload, RobotAckMessage.class);
        } catch (JacksonException e) {
            log.warn("로보독이 보낸 메시지를 해석하지 못했습니다: {}", LogSanitizer.clean(payload));
            return;
        }
        if (ack == null || ack.commandId() == null) {
            log.warn("로보독이 보낸 메시지에 commandId가 없습니다: {}", LogSanitizer.clean(payload));
            return;
        }
        CommandType type = recentCommands.remove(ack.commandId());
        if (type == null) {
            log.warn("보낸 적 없는(또는 이미 처리된) 명령 {}의 결과를 받았습니다: {}",
                    ack.commandId(), LogSanitizer.clean(ack.result()));
        } else if ("OK".equalsIgnoreCase(ack.result())) {
            log.info("명령 {}({}) 실행 완료", ack.commandId(), type);
        } else {
            log.warn("명령 {}({}) 실행 실패: {}", ack.commandId(), type, LogSanitizer.clean(ack.result()));
        }
    }

    private RobotCommandMessage dispatch(CommandType type, Integer speed, Integer durationMs) {
        return dispatch(type, speed, durationMs, null);
    }

    private RobotCommandMessage dispatch(CommandType type, Integer speed, Integer durationMs, Integer degrees) {
        RobotCommandMessage message = new RobotCommandMessage(
                commandSequence.incrementAndGet(), type, speed, durationMs, degrees
        );
        commandSender.send(message);
        recentCommands.put(message.commandId(), type);
        return message;
    }
}
