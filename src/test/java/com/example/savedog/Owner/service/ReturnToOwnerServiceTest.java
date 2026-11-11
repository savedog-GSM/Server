package com.example.savedog.Owner.service;

import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.service.PathfindingService;
import com.example.savedog.Robot.DTO.RobotCommandRequest;
import com.example.savedog.Robot.domain.CommandType;
import com.example.savedog.Robot.service.RobotService;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Telemetry.DTO.LocationRequest;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.Telemetry.repository.TelemetryRepository;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 좌표는 기준점에서 북쪽/동쪽으로 몇 m인지로 적는다.
 * 로보독에 달린 폰 위치는 실제 LocationService에, 로보독에 보낸 명령은 commands에 쌓인다.
 */
class ReturnToOwnerServiceTest {
    private static final double BASE_LATITUDE = 37.5665;
    private static final double BASE_LONGITUDE = 126.9780;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;

    private final MutableClock clock = new MutableClock();
    private final LocationService locations = new LocationService(clock);
    private final PathfindingService pathfinding = mock(PathfindingService.class);
    private final RobotService robot = mock(RobotService.class);
    private final OwnerSightingService sighting = mock(OwnerSightingService.class);
    private final TelemetryRepository telemetry = mock(TelemetryRepository.class);
    private final List<RobotCommandRequest> commands = new ArrayList<>();
    private ReturnToOwnerService service;

    @BeforeEach
    void setUp() {
        when(robot.sendCommand(any())).thenAnswer(invocation -> {
            commands.add(invocation.getArgument(0));
            return null;
        });
        when(sighting.recentSighting()).thenReturn(Optional.empty());
        when(telemetry.findFirstByOrderByRecordedAtDesc()).thenReturn(Optional.empty());
        // 경로는 로보독 → 주인 직선 하나
        when(pathfinding.plan(any(), any(), any())).thenAnswer(invocation -> Optional.of(new PathSearchResponse(
                PathMode.SAFEST, List.of(invocation.getArgument(0), invocation.getArgument(1)), 0, 1, SafetyLevel.SAFE)));
        service = new ReturnToOwnerService(locations, pathfinding, robot, sighting, telemetry, clock);
    }

    @Test
    @DisplayName("로보독 위치가 없으면 멈춰서 기다린다")
    void waitsForRobotLocation() {
        var response = service.start(at(0, 100).latitude(), at(0, 100).longitude(), 5.0);

        assertEquals(ReturnStatus.WAITING_FOR_ROBOT_LOCATION, response.status());
        assertEquals(List.of(CommandType.STOP), types());
    }

    @Test
    @DisplayName("방향을 모르면 앞으로 조금 가서 GPS가 움직인 방향으로 알아낸 뒤, 주인 쪽으로 돈다")
    void findsHeadingThenTurnsTowardOwner() {
        robotAt(0, 0);
        // 주인은 동쪽 100m
        var response = service.start(at(0, 100).latitude(), at(0, 100).longitude(), null);
        assertEquals(ReturnStatus.FINDING_HEADING, response.status());
        assertEquals(CommandType.FORWARD, last().type());
        assertEquals(ReturnToOwnerService.FORWARD_MS, last().durationMs());

        // 앞으로 가 보니 북쪽으로 6m 움직였다 → 바라보는 방향 북쪽(0°), 가야 할 방향 동쪽(90°) → 오른쪽으로 90°
        clock.advanceSeconds(2);
        robotAt(6, 0);
        service.tick();
        assertEquals(ReturnStatus.TURNING, service.snapshot().status());
        assertEquals(CommandType.TURN_RIGHT, last().type());
        assertEquals(90, last().degrees(), 5);

        // 도는 동안(2.5초)은 새 명령을 보내지 않는다.
        int sent = commands.size();
        clock.advanceSeconds(1);
        robotAt(6, 0);
        service.tick();
        assertEquals(sent, commands.size());

        // 다 돌았으면 돈 방향(동쪽)을 믿고 앞으로 간다.
        clock.advanceSeconds(2);
        robotAt(6, 0);
        service.tick();
        assertEquals(ReturnStatus.NAVIGATING, service.snapshot().status());
        assertEquals(CommandType.FORWARD, last().type());
    }

    @Test
    @DisplayName("이미 주인 쪽으로 움직이고 있으면 계속 앞으로 간다")
    void keepsGoingWhenAligned() {
        robotAt(0, 0);
        service.start(at(100, 0).latitude(), at(100, 0).longitude(), null);
        clock.advanceSeconds(2);
        robotAt(6, 0);

        service.tick();

        assertEquals(ReturnStatus.NAVIGATING, service.snapshot().status());
        assertEquals(CommandType.FORWARD, last().type());
        assertEquals(ReturnToOwnerService.SPEED, last().speed());
    }

    @Test
    @DisplayName("GPS로 주인 8m 안에 오면 멈추고 짖고 끝낸다")
    void arrivesByGps() {
        robotAt(0, 0);

        var response = service.start(at(5, 0).latitude(), at(5, 0).longitude(), null);

        assertEquals(ReturnStatus.ARRIVED, response.status());
        assertFalse(response.active());
        assertEquals(List.of(CommandType.STOP, CommandType.BARK), types());
    }

    @Test
    @DisplayName("앞에 장애물(위험 등급)이 있으면 앞으로 가지 않고 한 번만 멈춘다")
    void blockedByObstacle() {
        Telemetry danger = new Telemetry(null, 15);
        danger.applyRisk(SafetyLevel.DANGER, 5, "전방 15cm 장애물");
        ReflectionTestUtils.setField(danger, "recordedAt", LocalDateTime.now(clock));
        when(telemetry.findFirstByOrderByRecordedAtDesc()).thenReturn(Optional.of(danger));
        robotAt(0, 0);

        service.start(at(100, 0).latitude(), at(100, 0).longitude(), null);
        robotAt(0, 0);
        service.tick();

        assertEquals(ReturnStatus.BLOCKED_BY_OBSTACLE, service.snapshot().status());
        assertEquals(List.of(CommandType.STOP), types());
    }

    @Test
    @DisplayName("안전한 경로가 없으면 멈춰서 기다리고 30초 뒤 다시 찾는다")
    void noSafeRoute() {
        org.mockito.Mockito.doReturn(Optional.empty()).when(pathfinding).plan(any(), any(), any());
        robotAt(0, 0);

        service.start(at(100, 0).latitude(), at(100, 0).longitude(), null);
        assertEquals(ReturnStatus.NO_SAFE_ROUTE, service.snapshot().status());
        clock.advanceSeconds(10);
        robotAt(0, 0);
        service.tick();
        org.mockito.Mockito.verify(pathfinding, org.mockito.Mockito.times(1)).plan(any(), any(), any());

        clock.advanceSeconds(25);
        robotAt(0, 0);
        service.tick();
        org.mockito.Mockito.verify(pathfinding, org.mockito.Mockito.times(2)).plan(any(), any(), any());
    }

    @Test
    @DisplayName("카메라에 주인이 보이면 얼굴 쪽으로 돌고, 가운데면 다가가고, 크게 보이면 도착")
    void approachesByFace() {
        robotAt(0, 0);
        when(sighting.recentSighting()).thenReturn(Optional.of(face(0.5, 0.1)));
        service.start(at(30, 0).latitude(), at(30, 0).longitude(), null);
        assertEquals(ReturnStatus.APPROACHING_BY_FACE, service.snapshot().status());
        assertEquals(CommandType.TURN_RIGHT, last().type());
        assertEquals(15, last().degrees());

        clock.advanceSeconds(3);
        robotAt(0, 0);
        when(sighting.recentSighting()).thenReturn(Optional.of(face(0.05, 0.1)));
        service.tick();
        assertEquals(CommandType.FORWARD, last().type());

        clock.advanceSeconds(1);
        robotAt(0, 0);
        when(sighting.recentSighting()).thenReturn(Optional.of(face(0, 0.3)));
        service.tick();
        assertEquals(ReturnStatus.ARRIVED, service.snapshot().status());
    }

    @Test
    @DisplayName("로보독이 끊겨 있으면 상태로 알려 주고, 15분이 지나면 그만둔다")
    void disconnectedAndGiveUp() {
        when(robot.sendCommand(any())).thenThrow(new BusinessException(ErrorCode.ROBOT_NOT_CONNECTED));
        robotAt(0, 0);
        service.start(at(100, 0).latitude(), at(100, 0).longitude(), null);
        assertEquals(ReturnStatus.ROBOT_DISCONNECTED, service.snapshot().status());

        clock.advance(Duration.ofMinutes(16));
        robotAt(0, 0);
        service.tick();
        assertEquals(ReturnStatus.GAVE_UP, service.snapshot().status());
        assertFalse(service.snapshot().active());
    }

    @Test
    @DisplayName("멈추면 STOP을 보내고 IDLE이 된다. 위치를 다시 보내면 다시 시작한다")
    void stopAndRestart() {
        robotAt(0, 0);
        service.start(at(100, 0).latitude(), at(100, 0).longitude(), null);

        var stopped = service.stop();
        assertEquals(ReturnStatus.IDLE, stopped.status());
        assertEquals(CommandType.STOP, last().type());

        assertTrue(service.start(at(100, 0).latitude(), at(100, 0).longitude(), null).active());
    }

    @Test
    @DisplayName("잘못된 주인 위치는 400")
    void rejectsBadTarget() {
        assertError(ErrorCode.INVALID_REQUEST, () -> service.start(null, 127.0, null));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.start(91.0, 127.0, null));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.start(37.5, 127.0, -1.0));
    }

    @Test
    @DisplayName("방향 계산: 북쪽 0°, 동쪽 90°, 차이는 -180~180°로")
    void bearingMath() {
        assertEquals(0, ReturnToOwnerService.bearing(at(0, 0), at(10, 0)), 0.5);
        assertEquals(90, ReturnToOwnerService.bearing(at(0, 0), at(0, 10)), 0.5);
        assertEquals(180, ReturnToOwnerService.bearing(at(0, 0), at(-10, 0)), 0.5);
        assertEquals(-90, ReturnToOwnerService.normalize(270), 1e-9);
        assertEquals(20, ReturnToOwnerService.normalize(380), 1e-9);
        assertEquals(-170, ReturnToOwnerService.normalize(190), 1e-9);
    }

    private OwnerSighting face(double horizontal, double widthRatio) {
        return new OwnerSighting(LocalDateTime.now(clock), LocalDateTime.now(clock), 1, true, 0.8,
                horizontal, 0.0, OwnerSighting.Direction.CENTER, widthRatio);
    }

    private void robotAt(double north, double east) {
        GeoPoint point = at(north, east);
        locations.store(new LocationRequest(point.latitude(), point.longitude(), 5.0));
    }

    private RobotCommandRequest last() {
        return commands.getLast();
    }

    private List<CommandType> types() {
        return commands.stream().map(RobotCommandRequest::type).toList();
    }

    private static GeoPoint at(double northMeters, double eastMeters) {
        return new GeoPoint(
                BASE_LATITUDE + northMeters / METERS_PER_LATITUDE_DEGREE,
                BASE_LONGITUDE + eastMeters / (METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(BASE_LATITUDE)))
        );
    }
}
