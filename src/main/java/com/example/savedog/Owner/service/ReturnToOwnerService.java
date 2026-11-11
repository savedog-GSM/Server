package com.example.savedog.Owner.service;

import com.example.savedog.Owner.DTO.ReturnToOwnerResponse;
import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.service.PathfindingService;
import com.example.savedog.Robot.DTO.RobotCommandRequest;
import com.example.savedog.Robot.domain.CommandType;
import com.example.savedog.Robot.service.RobotService;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Telemetry.domain.RobotLocation;
import com.example.savedog.Telemetry.repository.TelemetryRepository;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.validation.RequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * 놓친 로보독이 주인에게 돌아가게 한다. 1초마다 아래를 반복한다.
 * 1) 로보독 위치: 로보독에 달린 폰의 GPS(POST /api/robot/location). 주인 위치: 주인 폰이 보낸 좌표(POST /api/owner/return)
 * 2) 로보독 → 주인 안전 경로를 찾고(도로 + 위험도), 다음 지점까지의 방향을 구한다.
 * 3) 로보독이 바라보는 방향은 최근 몇 초 동안 GPS가 움직인 방향으로 어림한다. 모르면 앞으로 조금 가 본다.
 * 4) 방향이 35° 넘게 틀어져 있으면 그만큼 돌고, 맞으면 1.5초만 앞으로 간다. (서버가 멈춰도 로보독이 곧 선다)
 * 5) 카메라에 주인 얼굴이 보이면 GPS 대신 얼굴을 보고 다가가고, 얼굴이 충분히 크게 보이거나 GPS로 8m 안이면 멈추고 짖는다.
 * 앞에 장애물(위험 등급)이 있으면 앞으로 가지 않는다. GPS 오차가 5~10m라 정확히 발밑까지는 못 간다.
 */
@Slf4j
@Service
public class ReturnToOwnerService {
    private static final Duration ROBOT_LOCATION_MAX_AGE = Duration.ofSeconds(10);
    private static final Duration GIVE_UP_AFTER = Duration.ofMinutes(15);
    private static final Duration REPLAN_EVERY = Duration.ofSeconds(30);
    private static final Duration HEADING_WINDOW = Duration.ofSeconds(8);
    private static final Duration ASSUMED_HEADING_VALID = Duration.ofSeconds(6);
    private static final Duration TURN_SETTLE = Duration.ofMillis(2500);
    private static final Duration DANGER_FRESH = Duration.ofSeconds(3);
    static final double ARRIVE_METERS = 8;
    private static final double WAYPOINT_REACHED_METERS = 8;
    private static final double OFF_ROUTE_METERS = 25;
    private static final double TARGET_MOVED_METERS = 15;
    private static final double HEADING_MIN_MOVE_METERS = 4;
    private static final double TURN_THRESHOLD_DEGREES = 35;
    static final int SPEED = 40;
    static final int FORWARD_MS = 1500;
    // 얼굴을 보고 다가갈 때: 얼굴 너비가 사진의 22% 이상이면 도착, 가로로 ±0.15 넘게 벗어나면 그쪽으로 돈다.
    static final double FACE_ARRIVE_WIDTH = 0.22;
    private static final double FACE_CENTER_TOLERANCE = 0.15;
    // 폰 카메라 화면 가로 끝이 중심에서 대략 몇 도인지 (화면 위치 → 돌 각도)
    private static final double CAMERA_HALF_VIEW_DEGREES = 30;
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private final LocationService locationService;
    private final PathfindingService pathfindingService;
    private final RobotService robotService;
    private final OwnerSightingService ownerSightingService;
    private final TelemetryRepository telemetryRepository;
    private final Clock clock;

    private boolean active;
    private ReturnStatus status = ReturnStatus.IDLE;
    private String message = "주인에게 돌아가기가 꺼져 있습니다.";
    private GeoPoint target;
    private Double targetAccuracy;
    private LocalDateTime targetUpdatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private List<GeoPoint> route = List.of();
    private int waypoint;
    private LocalDateTime routePlannedAt;
    private GeoPoint routePlannedTarget;
    private GeoPoint robot;
    private CommandType lastCommand;
    private LocalDateTime lastCommandAt;
    private Double assumedHeading;
    private LocalDateTime assumedHeadingAt;
    private final Deque<Fix> track = new ArrayDeque<>();

    public ReturnToOwnerService(
            LocationService locationService,
            PathfindingService pathfindingService,
            RobotService robotService,
            OwnerSightingService ownerSightingService,
            TelemetryRepository telemetryRepository,
            Clock clock
    ) {
        this.locationService = locationService;
        this.pathfindingService = pathfindingService;
        this.robotService = robotService;
        this.ownerSightingService = ownerSightingService;
        this.telemetryRepository = telemetryRepository;
        this.clock = clock;
    }

    // 주인 위치를 받아 시작한다. 이미 돌아가는 중이면 주인 위치만 바꾼다. (주인 폰이 몇 초마다 다시 보내면 된다)
    public synchronized ReturnToOwnerResponse start(Double latitude, Double longitude, Double accuracy) {
        GeoPoint owner = RequestValidator.coordinate(latitude, longitude, "주인 위치");
        if (accuracy != null) {
            RequestValidator.nonNegative(accuracy, "accuracy");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!active) {
            active = true;
            startedAt = now;
            finishedAt = null;
            route = List.of();
            routePlannedAt = null;
            track.clear();
            assumedHeading = null;
            lastCommand = null;
            lastCommandAt = null;
            update(ReturnStatus.WAITING_FOR_ROBOT_LOCATION, "주인에게 돌아가기를 시작합니다.");
        }
        target = owner;
        targetAccuracy = accuracy;
        targetUpdatedAt = now;
        tick();
        return snapshot();
    }

    public synchronized ReturnToOwnerResponse stop() {
        if (active) {
            finish(ReturnStatus.IDLE, "주인에게 돌아가기를 멈췄습니다.");
            command(CommandType.STOP, null, null, null);
        }
        return snapshot();
    }

    public synchronized ReturnToOwnerResponse snapshot() {
        double distance = robot != null && target != null ? robot.distanceTo(target) : -1;
        return new ReturnToOwnerResponse(
                active,
                status,
                message,
                target,
                targetAccuracy,
                targetUpdatedAt,
                robot,
                distance < 0 ? null : Math.round(distance * 10) / 10.0,
                route,
                lastCommand,
                lastCommandAt,
                startedAt,
                finishedAt
        );
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    public synchronized void tick() {
        if (!active) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (Duration.between(startedAt, now).compareTo(GIVE_UP_AFTER) > 0) {
            finish(ReturnStatus.GAVE_UP, "15분 동안 주인에게 닿지 못해 그만두었습니다. 위치를 다시 보내면 다시 시작합니다.");
            command(CommandType.STOP, null, null, null);
            return;
        }
        // 회전은 끝날 때까지 시간이 걸리므로 그동안 새 명령을 보내지 않는다.
        if (lastCommand != null && (lastCommand == CommandType.TURN_LEFT || lastCommand == CommandType.TURN_RIGHT)
                && Duration.between(lastCommandAt, now).compareTo(TURN_SETTLE) < 0) {
            return;
        }

        Optional<RobotLocation> location = locationService.findRecent(ROBOT_LOCATION_MAX_AGE);
        if (location.isEmpty()) {
            update(ReturnStatus.WAITING_FOR_ROBOT_LOCATION, "로보독에 달린 폰의 위치가 10초 넘게 오지 않아 멈춰서 기다립니다.");
            stopOnce();
            return;
        }
        robot = new GeoPoint(location.get().latitude(), location.get().longitude());
        remember(robot, location.get().receivedAt());

        Optional<OwnerSighting> face = ownerSightingService.recentSighting();
        if (face.isPresent()) {
            approachByFace(face.get());
            return;
        }
        if (robot.distanceTo(target) <= ARRIVE_METERS) {
            arrive("GPS로 주인 곁(8m 안)에 도착했습니다.");
            return;
        }
        navigate(now);
    }

    private void approachByFace(OwnerSighting face) {
        if (face.faceWidthRatio() != null && face.faceWidthRatio() >= FACE_ARRIVE_WIDTH) {
            arrive("카메라로 주인을 확인하고 곁에 도착했습니다.");
            return;
        }
        double offset = face.horizontalOffset() == null ? 0 : face.horizontalOffset();
        if (Math.abs(offset) > FACE_CENTER_TOLERANCE) {
            int degrees = (int) Math.max(5, Math.round(Math.abs(offset) * CAMERA_HALF_VIEW_DEGREES));
            update(ReturnStatus.APPROACHING_BY_FACE, "카메라에 주인이 보여 그쪽으로 %d° 돕니다.".formatted(degrees));
            turn(offset > 0 ? CommandType.TURN_RIGHT : CommandType.TURN_LEFT, degrees, null);
            return;
        }
        if (dangerAhead()) {
            update(ReturnStatus.BLOCKED_BY_OBSTACLE, "주인이 보이지만 앞에 장애물이 있어 멈춰서 기다립니다.");
            stopOnce();
            return;
        }
        update(ReturnStatus.APPROACHING_BY_FACE, "카메라에 주인이 보여 앞으로 다가갑니다.");
        command(CommandType.FORWARD, SPEED, 1000, null);
    }

    private void navigate(LocalDateTime now) {
        if (needsRoute(now)) {
            Optional<PathSearchResponse> planned = plan();
            routePlannedAt = now;
            routePlannedTarget = target;
            if (planned.isEmpty()) {
                route = List.of();
                update(ReturnStatus.NO_SAFE_ROUTE, "위험 구간을 피해 주인에게 갈 경로가 없어 멈춰서 기다립니다. 30초 뒤 다시 찾습니다.");
                stopOnce();
                return;
            }
            route = planned.get().path();
            waypoint = 1;
        }
        if (route.isEmpty()) {
            stopOnce();
            return;
        }
        while (waypoint < route.size() - 1 && robot.distanceTo(route.get(waypoint)) <= WAYPOINT_REACHED_METERS) {
            waypoint++;
        }
        GeoPoint next = route.get(Math.min(waypoint, route.size() - 1));
        double bearing = bearing(robot, next);

        Double heading = heading(now);
        if (heading == null) {
            if (dangerAhead()) {
                update(ReturnStatus.BLOCKED_BY_OBSTACLE, "앞에 장애물이 있어 멈춰서 기다립니다.");
                stopOnce();
                return;
            }
            update(ReturnStatus.FINDING_HEADING, "로보독이 바라보는 방향을 알기 위해 앞으로 조금 갑니다.");
            command(CommandType.FORWARD, SPEED, FORWARD_MS, null);
            return;
        }
        double difference = normalize(bearing - heading);
        if (Math.abs(difference) > TURN_THRESHOLD_DEGREES) {
            int degrees = (int) Math.min(180, Math.round(Math.abs(difference)));
            update(ReturnStatus.TURNING, "다음 지점 쪽으로 %d° 돕니다.".formatted(degrees));
            turn(difference > 0 ? CommandType.TURN_RIGHT : CommandType.TURN_LEFT, degrees, bearing);
            return;
        }
        if (dangerAhead()) {
            update(ReturnStatus.BLOCKED_BY_OBSTACLE, "앞에 장애물이 있어 멈춰서 기다립니다.");
            stopOnce();
            return;
        }
        update(ReturnStatus.NAVIGATING, "주인에게 가는 중입니다. 남은 직선거리 %.0fm".formatted(robot.distanceTo(target)));
        command(CommandType.FORWARD, SPEED, FORWARD_MS, null);
    }

    private boolean needsRoute(LocalDateTime now) {
        if (route.isEmpty() || routePlannedAt == null) {
            return routePlannedAt == null || Duration.between(routePlannedAt, now).compareTo(REPLAN_EVERY) >= 0;
        }
        if (Duration.between(routePlannedAt, now).compareTo(REPLAN_EVERY) >= 0) {
            return true;
        }
        if (routePlannedTarget != null && routePlannedTarget.distanceTo(target) > TARGET_MOVED_METERS) {
            return true;
        }
        // 경로에서 많이 벗어났으면 지금 자리에서 다시 찾는다.
        double nearest = route.stream().mapToDouble(robot::distanceTo).min().orElse(Double.MAX_VALUE);
        return nearest > OFF_ROUTE_METERS;
    }

    private Optional<PathSearchResponse> plan() {
        try {
            return pathfindingService.plan(robot, target, PathMode.SAFEST);
        } catch (RuntimeException e) {
            log.warn("주인에게 가는 경로를 찾지 못했습니다: {}", e.toString());
            return Optional.empty();
        }
    }

    // 최근 몇 초 동안 4m 넘게 움직였으면 그 방향, 방금 돌았으면 돈 뒤의 방향, 둘 다 없으면 모름(null)
    private Double heading(LocalDateTime now) {
        Fix newest = track.peekLast();
        Fix oldest = track.peekFirst();
        if (newest != null && oldest != null && newest != oldest
                && oldest.point().distanceTo(newest.point()) >= HEADING_MIN_MOVE_METERS) {
            return bearing(oldest.point(), newest.point());
        }
        if (assumedHeading != null && Duration.between(assumedHeadingAt, now).compareTo(ASSUMED_HEADING_VALID) <= 0) {
            return assumedHeading;
        }
        return null;
    }

    private void remember(GeoPoint point, LocalDateTime receivedAt) {
        Fix last = track.peekLast();
        if (last == null || !last.receivedAt().equals(receivedAt)) {
            track.addLast(new Fix(point, receivedAt));
        }
        while (!track.isEmpty() && Duration.between(track.peekFirst().receivedAt(), receivedAt).compareTo(HEADING_WINDOW) > 0) {
            track.removeFirst();
        }
    }

    private boolean dangerAhead() {
        return telemetryRepository.findFirstByOrderByRecordedAtDesc()
                .filter(latest -> latest.getRecordedAt() != null
                        && Duration.between(latest.getRecordedAt(), LocalDateTime.now(clock)).compareTo(DANGER_FRESH) <= 0)
                .map(latest -> latest.getRiskLevel() == SafetyLevel.DANGER)
                .orElse(false);
    }

    private void arrive(String reason) {
        command(CommandType.STOP, null, null, null);
        command(CommandType.BARK, null, 1000, null);
        finish(ReturnStatus.ARRIVED, reason);
    }

    private void turn(CommandType type, int degrees, Double headingAfterTurn) {
        if (command(type, SPEED, null, degrees)) {
            // 돌고 나면 예전 GPS 이동 방향은 맞지 않으므로 지운다.
            track.clear();
            assumedHeading = headingAfterTurn;
            assumedHeadingAt = LocalDateTime.now(clock);
        }
    }

    private void stopOnce() {
        if (lastCommand != CommandType.STOP) {
            command(CommandType.STOP, null, null, null);
        }
    }

    // 보냈으면 true. 로보독이 끊겨 있으면 상태만 바꾸고 다음 번에 다시 시도한다.
    private boolean command(CommandType type, Integer speed, Integer durationMs, Integer degrees) {
        try {
            robotService.sendCommand(new RobotCommandRequest(type, speed, durationMs, degrees));
            lastCommand = type;
            lastCommandAt = LocalDateTime.now(clock);
            return true;
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.ROBOT_NOT_CONNECTED) {
                // 멈추기만 실패한 경우는 지금 상태(위치 기다림 등)를 그대로 보여 준다.
                if (active && type != CommandType.STOP) {
                    update(ReturnStatus.ROBOT_DISCONNECTED, "로보독이 서버에 연결되어 있지 않아 명령을 보내지 못했습니다.");
                }
                return false;
            }
            throw e;
        }
    }

    private void update(ReturnStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    private void finish(ReturnStatus status, String message) {
        active = false;
        finishedAt = LocalDateTime.now(clock);
        update(status, message);
    }

    // a에서 b로 가는 방향. 북쪽 0°, 동쪽 90° (시계 방향)
    static double bearing(GeoPoint a, GeoPoint b) {
        double latitudeA = Math.toRadians(a.latitude());
        double latitudeB = Math.toRadians(b.latitude());
        double deltaLongitude = Math.toRadians(b.longitude() - a.longitude());
        double y = Math.sin(deltaLongitude) * Math.cos(latitudeB);
        double x = Math.cos(latitudeA) * Math.sin(latitudeB)
                - Math.sin(latitudeA) * Math.cos(latitudeB) * Math.cos(deltaLongitude);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    // -180° 초과 180° 이하로. 양수면 오른쪽(시계 방향)으로 돌아야 한다.
    static double normalize(double degrees) {
        double value = ((degrees % 360) + 360) % 360;
        return value > 180 ? value - 360 : value;
    }

    private record Fix(GeoPoint point, LocalDateTime receivedAt) {
    }
}
