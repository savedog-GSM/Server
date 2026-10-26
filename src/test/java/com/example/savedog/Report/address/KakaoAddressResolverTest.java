package com.example.savedog.Report.address;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 실제 카카오 응답(서울시청 좌표, 2026-10-21에 받은 것)을 돌려주는 가짜 카카오 서버로 확인한다.
class KakaoAddressResolverTest {
    private static final String CITY_HALL = """
            {"meta":{"total_count":1},"documents":[{"road_address":{"address_name":"서울특별시 중구 세종대로 110","region_1depth_name":"서울","region_2depth_name":"중구","region_3depth_name":"","road_name":"세종대로","underground_yn":"N","main_building_no":"110","sub_building_no":"","building_name":"서울특별시청","zone_no":"04524"},"address":{"address_name":"서울 중구 태평로1가 31","region_1depth_name":"서울","region_2depth_name":"중구","region_3depth_name":"태평로1가","mountain_yn":"N","main_address_no":"31","sub_address_no":"","zip_code":""}}]}""";
    private static final String LOT_ONLY = """
            {"meta":{"total_count":1},"documents":[{"road_address":null,"address":{"address_name":"서울 중구 태평로1가 31"}}]}""";
    private static final String NOTHING = """
            {"meta":{"total_count":0},"documents":[]}""";

    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("도로명 주소를 쓰고, 경도를 x·위도를 y로 KakaoAK 키와 함께 보낸다")
    void usesRoadAddress() throws IOException {
        KakaoAddressResolver resolver = resolverAnswering(200, CITY_HALL);

        Optional<String> address = resolver.resolve(37.5665, 126.978);

        assertEquals(Optional.of("서울특별시 중구 세종대로 110"), address);
        assertEquals("x=126.978&y=37.5665", query.get());
        assertEquals("KakaoAK test-key", authorization.get());
    }

    @Test
    @DisplayName("도로명 주소가 없으면 지번 주소를 쓴다")
    void fallsBackToLotAddress() throws IOException {
        assertEquals(Optional.of("서울 중구 태평로1가 31"), resolverAnswering(200, LOT_ONLY).resolve(37.5665, 126.978));
    }

    @Test
    @DisplayName("결과가 없거나 키가 틀려 401이면 주소 없이 넘어간다(예외를 내지 않는다)")
    void emptyOnNoResultOrError() throws IOException {
        assertTrue(resolverAnswering(200, NOTHING).resolve(37.5665, 126.978).isEmpty());
        stop();
        assertTrue(resolverAnswering(401, "{\"errorType\":\"AccessDeniedError\",\"message\":\"wrong appKey\"}")
                .resolve(37.5665, 126.978).isEmpty());
    }

    private KakaoAddressResolver resolverAnswering(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/local/geo/coord2address.json", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new KakaoAddressResolver(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", JsonMapper.builder().build()
        );
    }
}
