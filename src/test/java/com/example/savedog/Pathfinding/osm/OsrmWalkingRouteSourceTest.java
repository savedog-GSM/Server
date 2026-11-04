package com.example.savedog.Pathfinding.osm;

import com.example.savedog.Pathfinding.osm.WalkingRouteSource.WalkingRouteUnavailableException;
import com.example.savedog.global.common.GeoPoint;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 실제 OSRM 응답 모양(2026-11-04 routing.openstreetmap.de에서 받은 것을 줄임)을 돌려주는 가짜 서버로 확인한다.
class OsrmWalkingRouteSourceTest {
    private static final String TWO_ROUTES = """
            {"code":"Ok","routes":[
              {"geometry":{"coordinates":[[126.978001,37.566466],[126.977931,37.566465],[126.975,37.565]],"type":"LineString"},
               "distance":548.1,"duration":438.5},
              {"geometry":{"coordinates":[[126.978001,37.566466],[126.978416,37.566472],[126.975,37.565]],"type":"LineString"},
               "distance":557.5,"duration":445.9}],
             "waypoints":[{"name":"","location":[126.978001,37.566466],"distance":3.8},
                          {"name":"","location":[126.975,37.565],"distance":10.3}]}""";

    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> userAgent = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("경도,위도 순서로 묻고, 후보 경로들을 위도·경도 좌표로 바꾼다")
    void parsesRoutes() throws IOException {
        List<List<GeoPoint>> routes = sourceAnswering(200, TWO_ROUTES)
                .routes(List.of(new GeoPoint(37.5665, 126.978), new GeoPoint(37.565, 126.975)), true);

        assertEquals(2, routes.size());
        assertEquals(new GeoPoint(37.566466, 126.978001), routes.getFirst().getFirst());
        assertEquals("/route/v1/foot/126.978000,37.566500;126.975000,37.565000", path.get());
        assertTrue(query.get().contains("alternatives=3") && query.get().contains("geometries=geojson"), query.get());
        assertTrue(userAgent.get().startsWith("savedog-server"), userAgent.get());
    }

    @Test
    @DisplayName("거쳐 갈 곳이 있으면 후보를 따로 받지 않는다")
    void noAlternativesWithVia() throws IOException {
        sourceAnswering(200, TWO_ROUTES).routes(List.of(
                new GeoPoint(37.5665, 126.978), new GeoPoint(37.566, 126.977), new GeoPoint(37.565, 126.975)), true);

        assertTrue(query.get().contains("alternatives=false"), query.get());
    }

    @Test
    @DisplayName("이어진 길이 없으면(NoRoute) 빈 목록, 서버 오류나 잘못된 응답이면 쓸 수 없음 예외")
    void errors() throws IOException {
        assertTrue(sourceAnswering(400, "{\"code\":\"NoRoute\",\"message\":\"Impossible route\"}")
                .routes(List.of(new GeoPoint(37.5, 127.0), new GeoPoint(37.6, 127.1)), true).isEmpty());
        stop();
        assertThrows(WalkingRouteUnavailableException.class, () -> sourceAnswering(429, "Too Many Requests")
                .routes(List.of(new GeoPoint(37.5, 127.0), new GeoPoint(37.6, 127.1)), true));
        stop();
        assertThrows(WalkingRouteUnavailableException.class, () -> sourceAnswering(200, "<html>busy</html>")
                .routes(List.of(new GeoPoint(37.5, 127.0), new GeoPoint(37.6, 127.1)), true));
        stop();
        assertThrows(WalkingRouteUnavailableException.class, () -> sourceAnswering(400, "{\"code\":\"InvalidQuery\"}")
                .routes(List.of(new GeoPoint(37.5, 127.0), new GeoPoint(37.6, 127.1)), true));
    }

    private OsrmWalkingRouteSource sourceAnswering(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getQuery());
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new OsrmWalkingRouteSource("http://127.0.0.1:" + server.getAddress().getPort() + "/", JsonMapper.builder().build());
    }
}
