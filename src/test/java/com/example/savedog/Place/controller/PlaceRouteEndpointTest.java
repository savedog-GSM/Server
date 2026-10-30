package com.example.savedog.Place.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 자동 길찾기 주소의 인증·요청 형식을 실제 HTTP로 확인한다. (테스트에는 카카오 키가 없다)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:place-route;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=device-token-for-place-route",
                "savedog.security.operator-token=" + PlaceRouteEndpointTest.OPERATOR_TOKEN,
                "savedog.kakao.rest-api-key="
        }
)
@ActiveProfiles("test")
class PlaceRouteEndpointTest {
    static final String OPERATOR_TOKEN = "operator-token-for-place-route";

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("토큰 없이 부를 수 있고, 잘못된 mode·type은 400, 카카오 키가 없으면 503")
    void endpointContract() throws Exception {
        String path = "/api/places/nearest/route?type=toilet&latitude=35.143&longitude=126.8";

        // 토큰이 없거나 틀려도 인증에서 막히지 않는다. (테스트에는 카카오 키가 없어서 503)
        assertEquals(503, get(path, null).statusCode());
        assertEquals(503, get(path, "Bearer wrong-token-wrong-token").statusCode());

        HttpResponse<String> badMode = get(path + "&mode=FASTEST", "Bearer " + OPERATOR_TOKEN);
        assertEquals(400, badMode.statusCode(), badMode.body());
        HttpResponse<String> badType = get("/api/places/nearest/route?type=hospital&latitude=35.1&longitude=126.8",
                "Bearer " + OPERATOR_TOKEN);
        assertEquals(400, badType.statusCode(), badType.body());
        HttpResponse<String> noType = get("/api/places/nearest/route?latitude=35.1&longitude=126.8", "Bearer " + OPERATOR_TOKEN);
        assertEquals(400, noType.statusCode(), noType.body());

        HttpResponse<String> noKey = get(path + "&mode=SAFEST", "Bearer " + OPERATOR_TOKEN);
        assertEquals(503, noKey.statusCode(), noKey.body());
        assertTrue(noKey.body().contains("KAKAO_API_KEY_MISSING"), noKey.body());
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
