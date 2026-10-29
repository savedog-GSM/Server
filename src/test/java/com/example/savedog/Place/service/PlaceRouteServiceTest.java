package com.example.savedog.Place.service;

import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.service.PathfindingService;
import com.example.savedog.Place.DTO.PlaceRouteResponse;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Telemetry.domain.RobotLocation;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceRouteServiceTest {
    private static final GeoPoint START = new GeoPoint(35.1430, 126.8000);
    // 가까운 순서: A(직선 80m) → B(150m) → C(300m)
    private static final GeoPoint A = new GeoPoint(35.1437, 126.8000);
    private static final GeoPoint B = new GeoPoint(35.1444, 126.8000);
    private static final GeoPoint C = new GeoPoint(35.1457, 126.8000);
    private static final String THREE_STORES = """
            {"meta":{"is_end":true},"documents":[
              %s, %s, %s
            ]}""".formatted(place("a", "A 편의점", A, 80), place("b", "B 편의점", B, 150), place("c", "C 편의점", C, 300));

    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestQuery = new AtomicReference<>();
    private final AtomicReference<String> answer = new AtomicReference<>(THREE_STORES);
    private final PathfindingService pathfinding = mock(PathfindingService.class);
    private final LocationService locations = mock(LocationService.class);
    private HttpServer kakao;
    private PlaceRouteService service;

    @BeforeEach
    void start() throws IOException {
        kakao = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        kakao.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestQuery.set(URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            byte[] bytes = answer.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        kakao.start();
        KakaoPlaceSearchClient client = new KakaoPlaceSearchClient(
                "http://127.0.0.1:" + kakao.getAddress().getPort(), "test-key", JsonMapper.builder().build());
        service = new PlaceRouteService(new NearbyPlaceService(client), pathfinding, locations);
    }

    @AfterEach
    void stop() {
        kakao.stop(0);
    }

    @Test
    @DisplayName("안전 우선이면 길이 × 평균 위험도가 가장 낮은 곳을 고른다: 가까워도 위험한 A보다 조금 먼 안전한 B")
    void safestPicksLowestRiskWeightedCost() {
        reachable(A, PathMode.SAFEST, 100, 3.0);   // 비용 300
        reachable(B, PathMode.SAFEST, 180, 1.0);   // 비용 180
        reachable(C, PathMode.SAFEST, 320, 1.0);   // 비용 320

        PlaceRouteResponse route = service.route("convenience", START.latitude(), START.longitude(), null, null);

        assertEquals("B 편의점", route.place().name());
        assertEquals(PathMode.SAFEST, route.path().mode());
        assertEquals(180, route.path().distanceMeters());
        assertEquals(3, route.checkedPlaces());
        assertEquals(3, route.reachablePlaces());
        assertEquals(START, route.start());
        assertEquals("/v2/local/search/category.json", requestPath.get());
        assertTrue(requestQuery.get().contains("category_group_code=CS2"), requestQuery.get());
        assertTrue(requestQuery.get().contains("size=" + PlaceRouteService.CANDIDATES), requestQuery.get());
        assertTrue(requestQuery.get().contains("radius=1000"), requestQuery.get());
    }

    @Test
    @DisplayName("최단 거리면 위험도를 보지 않고 가장 짧은 경로를 고른다")
    void shortestPicksShortestPath() {
        reachable(A, PathMode.SHORTEST, 100, 3.0);
        reachable(B, PathMode.SHORTEST, 180, 1.0);
        reachable(C, PathMode.SHORTEST, 320, 1.0);

        PlaceRouteResponse route = service.route("convenience", START.latitude(), START.longitude(), 500, PathMode.SHORTEST);

        assertEquals("A 편의점", route.place().name());
        assertTrue(requestQuery.get().contains("radius=500"), requestQuery.get());
    }

    @Test
    @DisplayName("가장 가까운 곳이 수집된 길로 이어지지 않으면 건너뛰고 갈 수 있는 곳을 고른다")
    void skipsUnreachable() {
        when(pathfinding.plan(eq(START), any(), eq(PathMode.SAFEST))).thenReturn(Optional.empty());
        reachable(C, PathMode.SAFEST, 350, 2.0);

        PlaceRouteResponse route = service.route("convenience", START.latitude(), START.longitude(), null, PathMode.SAFEST);

        assertEquals("C 편의점", route.place().name());
        assertEquals(1, route.reachablePlaces());
    }

    @Test
    @DisplayName("후보 모두 경로가 없으면 404 PATH_NOT_FOUND, 근처에 장소가 없으면 404 PLACE_NOT_FOUND")
    void notFound() {
        when(pathfinding.plan(any(), any(), any())).thenReturn(Optional.empty());
        assertError(ErrorCode.PATH_NOT_FOUND,
                () -> service.route("convenience", START.latitude(), START.longitude(), null, null));

        answer.set("{\"meta\":{\"is_end\":true},\"documents\":[]}");
        assertError(ErrorCode.PLACE_NOT_FOUND,
                () -> service.route("toilet", START.latitude(), START.longitude(), null, null));
        assertEquals("/v2/local/search/keyword.json", requestPath.get());
        assertTrue(requestQuery.get().contains("query=화장실"), requestQuery.get());
    }

    @Test
    @DisplayName("출발지를 빼면 스마트폰의 최근 위치(1분 이내)에서 출발하고, 그것도 없으면 409")
    void startsFromRecentPhoneLocation() {
        when(locations.findRecent(Duration.ofSeconds(60))).thenReturn(Optional.of(
                new RobotLocation(START.latitude(), START.longitude(), 5.0, LocalDateTime.now())));
        reachable(A, PathMode.SAFEST, 100, 1.0);

        PlaceRouteResponse route = service.route("convenience", null, null, null, null);
        assertEquals("A 편의점", route.place().name());
        assertEquals(START, route.start());

        when(locations.findRecent(Duration.ofSeconds(60))).thenReturn(Optional.empty());
        assertError(ErrorCode.LOCATION_UNAVAILABLE, () -> service.route("convenience", null, null, null, null));
    }

    @Test
    @DisplayName("잘못된 요청(모르는 종류, 위도만 있음, 범위 밖 반경)은 카카오를 부르지 않고 400")
    void rejectsBadRequests() {
        assertError(ErrorCode.INVALID_REQUEST, () -> service.route("hospital", 35.0, 126.0, null, null));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.route("toilet", 35.0, null, null, null));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.route("toilet", 35.0, 126.0, 20_001, null));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.route(null, 35.0, 126.0, null, null));

        assertEquals(null, requestPath.get());
        verify(pathfinding, never()).plan(any(), any(), any());
    }

    @Test
    @DisplayName("후보마다 한 번씩만 경로를 찾는다")
    void plansEachCandidateOnce() {
        when(pathfinding.plan(any(), any(), any())).thenReturn(Optional.empty());
        reachable(A, PathMode.SAFEST, 100, 1.0);

        service.route("convenience", START.latitude(), START.longitude(), null, null);

        verify(pathfinding, times(3)).plan(eq(START), any(), eq(PathMode.SAFEST));
    }

    private void reachable(GeoPoint end, PathMode mode, double meters, double averageRisk) {
        when(pathfinding.plan(START, end, mode)).thenReturn(Optional.of(new PathSearchResponse(
                mode, List.of(START, end), meters, averageRisk, SafetyLevel.fromRiskScore(averageRisk))));
    }

    private static String place(String id, String name, GeoPoint point, int distance) {
        return """
                {"id":"%s","place_name":"%s","category_name":"가정,생활 > 편의점","category_group_code":"CS2",
                 "category_group_name":"편의점","phone":"","address_name":"광주 광산구 월계동 %s",
                 "road_address_name":"광주 광산구 첨단로 %s","x":"%s","y":"%s","distance":"%d",
                 "place_url":"https://place.map.kakao.com/%s"}"""
                .formatted(id, name, id, id, point.longitude(), point.latitude(), distance, id);
    }
}
