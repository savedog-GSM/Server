package com.example.savedog.Pathfinding.osm;

import com.example.savedog.global.common.GeoPoint;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * OSRM 걷기 경로 서버(기본: FOSSGIS의 routing.openstreetmap.de)에 경로를 묻는다.
 * 요청: {baseUrl}/route/v1/foot/{경도},{위도};{경도},{위도}?overview=full&geometries=geojson
 * 공개 서버는 가벼운 사용만 허용하므로 요청을 묶거나 반복하지 않는다. 화면에는 "© OpenStreetMap contributors"를 표시해야 한다.
 */
public class OsrmWalkingRouteSource implements WalkingRouteSource {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(6);
    private static final String USER_AGENT = "savedog-server/0.1 (school project)";

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public OsrmWalkingRouteSource(String baseUrl, JsonMapper jsonMapper) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
        );
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.trim().replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                // deflate로 주면 압축 형식이 맞지 않아 풀지 못한다. (실제 서버에서 확인) gzip만 받는다.
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
                .build();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<List<GeoPoint>> routes(List<GeoPoint> waypoints, boolean alternatives) {
        // OSRM은 경도,위도 순서다.
        String coordinates = waypoints.stream()
                .map(point -> String.format(Locale.ROOT, "%.6f,%.6f", point.longitude(), point.latitude()))
                .collect(Collectors.joining(";"));
        String body;
        try {
            body = restClient.get()
                    .uri(uri -> uri.path("/route/v1/foot/" + coordinates)
                            .queryParam("overview", "full")
                            .queryParam("geometries", "geojson")
                            .queryParam("steps", "false")
                            .queryParam("alternatives", alternatives && waypoints.size() == 2 ? "3" : "false")
                            .build())
                    .retrieve()
                    // 갈 길이 없으면 400과 code=NoRoute를 준다. 그건 오류가 아니라 "경로 없음"이다.
                    .onStatus(status -> status.value() == 400, (request, response) -> { })
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new WalkingRouteUnavailableException("걷기 경로 서버에 닿지 못했습니다: " + e.getMessage(), e);
        }
        return parse(body);
    }

    private List<List<GeoPoint>> parse(String body) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(body == null ? "" : body);
        } catch (RuntimeException e) {
            throw new WalkingRouteUnavailableException("걷기 경로 서버의 응답을 읽지 못했습니다.", e);
        }
        String code = root.path("code").asString("");
        if (code.equals("NoRoute") || code.equals("NoSegment")) {
            return List.of();
        }
        if (!code.equals("Ok")) {
            throw new WalkingRouteUnavailableException("걷기 경로 서버 오류: " + code, null);
        }
        List<List<GeoPoint>> routes = new ArrayList<>();
        for (JsonNode route : root.path("routes")) {
            List<GeoPoint> points = new ArrayList<>();
            for (JsonNode coordinate : route.path("geometry").path("coordinates")) {
                if (coordinate.size() >= 2 && coordinate.get(0).isNumber() && coordinate.get(1).isNumber()) {
                    points.add(new GeoPoint(coordinate.get(1).asDouble(), coordinate.get(0).asDouble()));
                }
            }
            if (points.size() >= 2) {
                routes.add(List.copyOf(points));
            }
        }
        return routes;
    }
}
