package com.example.savedog.Robot.service;

import com.example.savedog.Robot.DTO.RobotCommandMessage;
import com.example.savedog.Robot.DTO.RobotCommandRequest;
import com.example.savedog.Robot.DTO.RobotCommandResponse;
import com.example.savedog.Robot.DTO.RobotStatusResponse;
import com.example.savedog.Robot.domain.CommandType;
import com.example.savedog.Robot.websocket.RobotCommandSender;
import com.example.savedog.Robot.websocket.RobotSessionRegistry;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.Telemetry.DTO.LocationRequest;
import com.example.savedog.Telemetry.repository.TelemetryRepository;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RobotServiceTest {
    private final RobotSessionRegistry registry = mock(RobotSessionRegistry.class);
    private final RobotCommandSender sender = mock(RobotCommandSender.class);
    private final TelemetryRepository telemetryRepository = mock(TelemetryRepository.class);
    private final LocationService locationService = new LocationService(new MutableClock());
    private final RobotService service = new RobotService(
            registry, sender, telemetryRepository, locationService, JsonMapper.builder().build()
    );

    @Test
    @DisplayName("이동 명령은 속도와 시간을 그대로 전달한다")
    void moveCarriesSpeedAndDuration() {
        RobotCommandResponse response = service.sendCommand(new RobotCommandRequest(CommandType.FORWARD, 60, 1500));

        RobotCommandMessage sent = sentMessage();
        assertEquals(CommandType.FORWARD, sent.type());
        assertEquals(60, sent.speed());
        assertEquals(1500, sent.durationMs());
        assertEquals(sent.commandId(), response.commandId());
    }

    @Test
    @DisplayName("짖기는 시간을 생략하면 1초다")
    void barkDefaultsToOneSecond() {
        service.sendCommand(new RobotCommandRequest(CommandType.BARK, null, null));

        assertEquals(1000, sentMessage().durationMs());
    }

    @Test
    @DisplayName("멈추기와 앉기는 딸려 온 속도·시간을 버린다")
    void stopAndSitIgnoreSpeedAndDuration() {
        service.sendCommand(new RobotCommandRequest(CommandType.SIT, 50, 3000));

        RobotCommandMessage sent = sentMessage();
        assertEquals(CommandType.SIT, sent.type());
        assertNull(sent.speed());
        assertNull(sent.durationMs());
    }

    @Test
    @DisplayName("명령마다 번호가 하나씩 늘어난다")
    void commandIdsIncrease() {
        long first = service.sendCommand(new RobotCommandRequest(CommandType.STOP, null, null)).commandId();
        long second = service.sendCommand(new RobotCommandRequest(CommandType.STOP, null, null)).commandId();

        assertEquals(first + 1, second);
    }

    @Test
    @DisplayName("종류가 없거나 값이 범위를 벗어나면 보내지 않는다")
    void rejectsInvalidCommands() {
        assertError(ErrorCode.INVALID_REQUEST, () -> service.sendCommand(null));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.sendCommand(new RobotCommandRequest(null, 10, 10)));
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.sendCommand(new RobotCommandRequest(CommandType.FORWARD, 101, null)));
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.sendCommand(new RobotCommandRequest(CommandType.FORWARD, -1, null)));
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.sendCommand(new RobotCommandRequest(CommandType.FORWARD, 50, 0)));
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.sendCommand(new RobotCommandRequest(CommandType.BARK, null, 60_001)));

        verify(sender, never()).send(any());
    }

    @Test
    @DisplayName("로보독이 연결되어 있지 않으면 ROBOT_NOT_CONNECTED")
    void notConnected() {
        doThrow(new BusinessException(ErrorCode.ROBOT_NOT_CONNECTED)).when(sender).send(any());

        assertError(ErrorCode.ROBOT_NOT_CONNECTED,
                () -> service.sendCommand(new RobotCommandRequest(CommandType.STOP, null, null)));
    }

    @Test
    @DisplayName("위험 시 자동 대응은 멈추기, 앉기, 짖기(1초)를 이 순서로 보낸다")
    void emergencyStopSendsStopSitThenBark() {
        assertTrue(service.emergencyStop());

        ArgumentCaptor<RobotCommandMessage> captor = ArgumentCaptor.forClass(RobotCommandMessage.class);
        verify(sender, times(3)).send(captor.capture());
        List<RobotCommandMessage> sent = captor.getAllValues();
        assertEquals(CommandType.STOP, sent.get(0).type());
        assertEquals(CommandType.SIT, sent.get(1).type());
        assertEquals(CommandType.BARK, sent.get(2).type());
        assertEquals(1000, sent.get(2).durationMs());
    }

    @Test
    @DisplayName("자동 대응은 로보독이 끊겨 있어도 예외를 내지 않고 false를 돌려준다")
    void emergencyStopDoesNotThrowWhenDisconnected() {
        doThrow(new BusinessException(ErrorCode.ROBOT_NOT_CONNECTED)).when(sender).send(any());

        assertFalse(assertDoesNotThrow(service::emergencyStop));
    }

    @Test
    @DisplayName("로보독이 이상한 메시지를 보내도 예외를 내지 않는다")
    void malformedAckIsIgnored() {
        assertDoesNotThrow(() -> service.handleAck("not json"));
        assertDoesNotThrow(() -> service.handleAck("{}"));
        assertDoesNotThrow(() -> service.handleAck("{\"commandId\":\"abc\"}"));
        assertDoesNotThrow(() -> service.handleAck("{\"commandId\":999,\"result\":\"OK\"}"));
        assertDoesNotThrow(() -> service.handleAck("[1,2,3]"));
        assertDoesNotThrow(() -> service.handleAck(""));
    }

    @Test
    @DisplayName("상태 조회는 가장 최근 센서 값과 위험도를 돌려준다")
    void statusShowsLatestTelemetry() {
        Telemetry telemetry = new Telemetry(null, 35.2);
        telemetry.applyRisk(SafetyLevel.CAUTION, 3, "전방 35cm 장애물");
        when(telemetryRepository.findFirstByOrderByRecordedAtDesc()).thenReturn(Optional.of(telemetry));
        when(registry.isConnected()).thenReturn(true);

        RobotStatusResponse status = service.getStatus();

        assertTrue(status.connected());
        assertEquals(telemetry.getRecordedAt(), status.lastSeenAt());
        assertEquals(35.2, status.sensor().distance());
        assertEquals(3, status.risk().score());
        assertNull(status.latitude());
        assertNull(status.lastLocationAt());
    }

    @Test
    @DisplayName("센서 값을 받은 적이 없으면 연결 여부만 채워진다")
    void statusBeforeAnyTelemetry() {
        when(telemetryRepository.findFirstByOrderByRecordedAtDesc()).thenReturn(Optional.empty());

        RobotStatusResponse status = service.getStatus();

        assertFalse(status.connected());
        assertNull(status.lastSeenAt());
        assertNull(status.sensor());
        assertNull(status.risk());
    }

    @Test
    @DisplayName("센서 기록이 없어도 마지막으로 받은 GPS 좌표를 상태에 돌려준다")
    void statusShowsLocationWithoutTelemetry() {
        locationService.store(new LocationRequest(35.14332, 126.80079, 5.0));
        when(telemetryRepository.findFirstByOrderByRecordedAtDesc()).thenReturn(Optional.empty());

        RobotStatusResponse status = service.getStatus();

        assertEquals(35.14332, status.latitude());
        assertEquals(126.80079, status.longitude());
        assertEquals(locationService.lastReceivedAt().orElseThrow(), status.lastLocationAt());
        assertNull(status.lastSeenAt());
        assertNull(status.sensor());
        assertNull(status.risk());
    }

    @Test
    @DisplayName("회전은 각도를 실어 보내고, 다른 명령에 딸려 온 각도는 버린다")
    void turnCarriesDegrees() {
        service.sendCommand(new RobotCommandRequest(CommandType.TURN_RIGHT, 30, null, 90));
        RobotCommandMessage turn = sentMessage();
        assertEquals(CommandType.TURN_RIGHT, turn.type());
        assertEquals(90, turn.degrees());
        assertEquals(30, turn.speed());
        assertNull(turn.durationMs());

        RobotCommandMessage forward = sendAndCapture(new RobotCommandRequest(CommandType.FORWARD, 50, 1000, 90));
        assertNull(forward.degrees());
    }

    @Test
    @DisplayName("각도가 범위를 벗어나거나 시간과 함께 오면 보내지 않는다")
    void rejectsInvalidDegrees() {
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.sendCommand(new RobotCommandRequest(CommandType.TURN_LEFT, null, null, 0)));
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.sendCommand(new RobotCommandRequest(CommandType.TURN_LEFT, null, null, 361)));
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.sendCommand(new RobotCommandRequest(CommandType.TURN_LEFT, null, 1000, 90)));
        verify(sender, never()).send(any());
    }

    @Test
    @DisplayName("비상 정지는 값을 싣지 않고, 재개는 속도만 싣는다")
    void emergencyStopAndResume() {
        RobotCommandMessage stop = sendAndCapture(new RobotCommandRequest(CommandType.EMERGENCY_STOP, 50, 1000, 90));
        assertEquals(CommandType.EMERGENCY_STOP, stop.type());
        assertNull(stop.speed());
        assertNull(stop.durationMs());
        assertNull(stop.degrees());

        RobotCommandMessage resume = sendAndCapture(new RobotCommandRequest(CommandType.RESUME, 40, 1000, 90));
        assertEquals(CommandType.RESUME, resume.type());
        assertEquals(40, resume.speed());
        assertNull(resume.durationMs());
        assertNull(resume.degrees());

        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.sendCommand(new RobotCommandRequest(CommandType.RESUME, 101, null)));
    }

    @Test
    @DisplayName("각도는 있을 때만 메시지에 실린다")
    void degreesOnlyInJsonWhenPresent() {
        JsonMapper json = JsonMapper.builder().build();

        String plain = json.writeValueAsString(new RobotCommandMessage(1L, CommandType.STOP, null, null));
        String turn = json.writeValueAsString(new RobotCommandMessage(2L, CommandType.TURN_LEFT, 50, null, 90));

        assertFalse(plain.contains("degrees"), plain);
        assertTrue(plain.contains("\"speed\":null"), plain);
        assertTrue(turn.contains("\"degrees\":90"), turn);
    }

    private RobotCommandMessage sendAndCapture(RobotCommandRequest request) {
        clearInvocations(sender);
        service.sendCommand(request);
        return sentMessage();
    }

    private RobotCommandMessage sentMessage() {
        ArgumentCaptor<RobotCommandMessage> captor = ArgumentCaptor.forClass(RobotCommandMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }
}
