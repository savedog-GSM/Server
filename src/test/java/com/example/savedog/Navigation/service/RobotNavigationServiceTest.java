package com.example.savedog.Navigation.service;

import com.example.savedog.Navigation.DTO.NavigationResponse;
import com.example.savedog.Navigation.domain.NavigationPurpose;
import com.example.savedog.Navigation.domain.NavigationStatus;
import com.example.savedog.Owner.service.OwnerSighting;
import com.example.savedog.Owner.service.OwnerSightingService;
import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.service.PathfindingService;
import com.example.savedog.Robot.DTO.RobotCommandRequest;
import com.example.savedog.Robot.domain.CommandType;
import com.example.savedog.Robot.service.RobotService;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Telemetry.DTO.LocationRequest;
import com.example.savedog.Telemetry.repository.TelemetryRepository;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 목적지 안내. 주행 자체(방향 찾기·회전·장애물 대기 등)는 ReturnToOwnerServiceTest에서도 같은 코드로 확인한다.
class RobotNavigationServiceTest {
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
    private RobotNavigationService navigation;

    @BeforeEach
    void setUp() {
        when(robot.sendCommand(any())).thenAnswer(invocation -> {
            commands.add(invocation.getArgument(0));
            return null;
        });
        when(sighting.recentSighting()).thenReturn(Optional.empty());
        when(telemetry.findFirstByOrderByRecordedAtDesc()).thenReturn(Optional.empty());
        when(pathfinding.plan(any(), any(), any())).thenAnswer(invocation -> Optional.of(new PathSearchResponse(
                invocation.getArgument(2), List.of(invocation.getArgument(0), invocation.getArgument(1)),
                100, 1.4, SafetyLevel.SAFE, 30, true)));
        navigation = new RobotNavigationService(locations, pathfinding, robot, sighting, telemetry, clock);
    }

    @Test
    @DisplayName("목적지로 출발하면 안전 경로를 찾아 응답에 싣고, 그 경로를 따라 움직이기 시작한다")
    void startsAlongSafePath() {
        robotAt(0, 0);

        NavigationResponse response = start(100, 0, null);

        assertTrue(response.active());
        assertEquals(NavigationPurpose.DESTINATION, response.purpose());
        assertEquals(PathMode.SAFEST, response.mode());
        assertEquals(NavigationStatus.FINDING_HEADING, response.status());
        assertNotNull(response.plannedPath());
        assertTrue(response.plannedPath().followsRoads());
        verify(pathfinding).plan(any(), any(), eq(PathMode.SAFEST));
        assertEquals(CommandType.FORWARD, commands.getLast().type());

        // 북쪽으로 6m 움직였다 → 목적지(북쪽)를 바라보고 있으니 계속 앞으로
        clock.advanceSeconds(2);
        robotAt(6, 0);
        navigation.tick();
        assertEquals(NavigationStatus.NAVIGATING, navigation.snapshot().status());
        assertEquals(CommandType.FORWARD, commands.getLast().type());
    }

    @Test
    @DisplayName("경로 방식을 최단(SHORTEST)으로 주면 그 방식으로 찾는다")
    void shortestMode() {
        robotAt(0, 0);

        assertEquals(PathMode.SHORTEST, start(100, 0, PathMode.SHORTEST).mode());

        verify(pathfinding).plan(any(), any(), eq(PathMode.SHORTEST));
    }

    @Test
    @DisplayName("목적지 8m 안이면 멈추고 짖고 끝낸다")
    void arrives() {
        robotAt(0, 0);

        NavigationResponse response = start(5, 0, null);

        assertEquals(NavigationStatus.ARRIVED, response.status());
        assertFalse(response.active());
        assertTrue(response.message().contains("목적지"));
    }

    @Test
    @DisplayName("목적지로 갈 때는 카메라에 주인이 보여도 얼굴 쪽으로 가지 않는다")
    void ignoresOwnerFaceForDestination() {
        when(sighting.recentSighting()).thenReturn(Optional.of(new OwnerSighting(
                LocalDateTime.now(clock), LocalDateTime.now(clock), 1, true, 0.9, 0.8, 0.0,
                OwnerSighting.Direction.RIGHT, 0.1)));
        robotAt(0, 0);

        start(100, 0, null);

        assertEquals(NavigationStatus.FINDING_HEADING, navigation.snapshot().status());
        assertEquals(CommandType.FORWARD, commands.getLast().type());
    }

    @Test
    @DisplayName("로보독은 한 대: 주인에게 돌아가기를 시작하면 목적지 안내는 그만두고, 주인용 멈춤은 목적지 안내를 멈추지 않는다")
    void onePurposeAtATime() {
        robotAt(0, 0);
        start(100, 0, null);

        navigation.stop(NavigationPurpose.OWNER);
        assertTrue(navigation.snapshot().active());

        GeoPoint owner = at(0, 100);
        NavigationResponse switched = navigation.start(NavigationPurpose.OWNER, owner.latitude(), owner.longitude(), 5.0, null);
        assertEquals(NavigationPurpose.OWNER, switched.purpose());
        assertEquals(owner, switched.target());

        NavigationResponse stopped = navigation.stop(null);
        assertFalse(stopped.active());
        assertEquals(NavigationStatus.IDLE, stopped.status());
        assertEquals(CommandType.STOP, commands.getLast().type());
    }

    @Test
    @DisplayName("같은 목적으로 다시 보내면 처음부터가 아니라 목적지만 바꾼다")
    void updatesTarget() {
        robotAt(0, 0);
        LocalDateTime startedAt = start(100, 0, null).startedAt();
        clock.advanceSeconds(3);
        robotAt(0, 0);

        NavigationResponse updated = start(200, 0, null);

        assertEquals(startedAt, updated.startedAt());
        assertEquals(at(200, 0), updated.target());
    }

    @Test
    @DisplayName("잘못된 목적지는 400")
    void rejectsBadTarget() {
        assertError(ErrorCode.INVALID_REQUEST,
                () -> navigation.start(NavigationPurpose.DESTINATION, null, 127.0, null, null));
        assertError(ErrorCode.INVALID_REQUEST,
                () -> navigation.start(NavigationPurpose.DESTINATION, 37.5, 181.0, null, null));
        assertFalse(navigation.snapshot().active());
    }

    private NavigationResponse start(double north, double east, PathMode mode) {
        GeoPoint goal = at(north, east);
        return navigation.start(NavigationPurpose.DESTINATION, goal.latitude(), goal.longitude(), null, mode);
    }

    private void robotAt(double north, double east) {
        GeoPoint point = at(north, east);
        locations.store(new LocationRequest(point.latitude(), point.longitude(), 5.0));
    }

    private static GeoPoint at(double northMeters, double eastMeters) {
        return new GeoPoint(
                BASE_LATITUDE + northMeters / METERS_PER_LATITUDE_DEGREE,
                BASE_LONGITUDE + eastMeters / (METERS_PER_LATITUDE_DEGREE * Math.cos(Math.toRadians(BASE_LATITUDE)))
        );
    }
}
