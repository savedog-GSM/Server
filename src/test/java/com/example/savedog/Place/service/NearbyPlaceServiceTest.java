package com.example.savedog.Place.service;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NearbyPlaceServiceTest {
    private static final String PLACE_RESULT = """
            {"meta":{"is_end":true},"documents":[{
              "id":"place-1","place_name":"가까운 장소","category_name":"생활 > 편의점",
              "category_group_code":"CS2","category_group_name":"편의점","phone":"062-123-4567",
              "address_name":"광주 광산구 월계동 1","road_address_name":"광주 광산구 첨단로 1",
              "x":"126.80079","y":"35.14332","distance":"123","place_url":"https://place.map.kakao.com/place-1"
            }]}
            """;

    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestQuery = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("편의점은 CS2 카테고리로 가까운 순서 검색하고 장소 정보를 변환한다")
    void searchesNearbyConvenienceStores() throws IOException {
        NearbyPlaceService service = serviceAnswering(200, PLACE_RESULT, "test-key");

        var response = service.search("convenience", 35.14332, 126.80079, 800, 1, 10);

        assertEquals("/v2/local/search/category.json", requestPath.get());
        assertEquals("KakaoAK test-key", authorization.get());
        Map<String, String> query = decodedQuery();
        assertEquals("CS2", query.get("category_group_code"));
        assertEquals("126.80079", query.get("x"));
        assertEquals("35.14332", query.get("y"));
        assertEquals("800", query.get("radius"));
        assertEquals("distance", query.get("sort"));
        assertEquals("1", query.get("page"));
        assertEquals("10", query.get("size"));

        assertEquals("convenience", response.type());
        assertEquals(35.14332, response.latitude());
        assertEquals(126.80079, response.longitude());
        assertEquals(800, response.radiusMeters());
        assertEquals(1, response.places().size());
        assertEquals("가까운 장소", response.places().getFirst().name());
        assertEquals(35.14332, response.places().getFirst().latitude());
        assertEquals(126.80079, response.places().getFirst().longitude());
        assertEquals(123.0, response.places().getFirst().distanceMeters());
        assertEquals("https://place.map.kakao.com/place-1", response.places().getFirst().placeUrl());
        assertTrue(!response.hasMore());
    }

    @Test
    @DisplayName("화장실은 키워드 검색을 사용한다")
    void searchesNearbyToiletsByKeyword() throws IOException {
        NearbyPlaceService service = serviceAnswering(200, PLACE_RESULT, "test-key");

        service.search("toilet", 35.14332, 126.80079, 1_000, 1, 15);

        assertEquals("/v2/local/search/keyword.json", requestPath.get());
        assertEquals("화장실", decodedQuery().get("query"));
    }

    @Test
    @DisplayName("카카오 키가 없으면 구체적인 서비스 오류를 반환한다")
    void reportsMissingKakaoKey() {
        NearbyPlaceService service = new NearbyPlaceService(
                new KakaoPlaceSearchClient("", JsonMapper.builder().build())
        );

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.search("toilet", 35.14332, 126.80079, null, null, null));

        assertEquals(ErrorCode.KAKAO_API_KEY_MISSING, error.getErrorCode());
    }

    @Test
    @DisplayName("카카오 인증 실패는 외부 장소 검색 오류로 바꿔 반환한다")
    void reportsKakaoFailure() throws IOException {
        NearbyPlaceService service = serviceAnswering(401, "{\"message\":\"wrong key\"}", "bad-key");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.search("convenience", 35.14332, 126.80079, 1_000, 1, 15));

        assertEquals(ErrorCode.KAKAO_PLACE_SEARCH_FAILED, error.getErrorCode());
    }

    @Test
    @DisplayName("지원하지 않는 장소 종류는 카카오에 요청하지 않고 거절한다")
    void rejectsUnknownPlaceType() {
        NearbyPlaceService service = new NearbyPlaceService(
                new KakaoPlaceSearchClient("test-key", JsonMapper.builder().build())
        );

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.search("cafe", 35.14332, 126.80079, null, null, null));

        assertEquals(ErrorCode.INVALID_REQUEST, error.getErrorCode());
    }

    private NearbyPlaceService serviceAnswering(int status, String body, String key) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestQuery.set(exchange.getRequestURI().getRawQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        KakaoPlaceSearchClient client = new KakaoPlaceSearchClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), key, JsonMapper.builder().build()
        );
        return new NearbyPlaceService(client);
    }

    private Map<String, String> decodedQuery() {
        return Arrays.stream(requestQuery.get().split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8)
                ));
    }
}
