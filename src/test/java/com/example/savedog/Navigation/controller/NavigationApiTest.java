package com.example.savedog.Navigation.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 목적지 안내 주소를 실제 HTTP로 확인한다. (테스트에는 로보독·폰 위치가 없다)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:navigation-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=device-token-for-navigation",
                "savedog.security.operator-token=" + NavigationApiTest.OPERATOR_TOKEN
        }
)
@ActiveProfiles("test")
class NavigationApiTest {
    static final String OPERATOR_TOKEN = "operator-token-for-navigation";

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("출발하면 로보독 위치를 기다리고, 상태를 보고, 멈출 수 있다. 주인 돌아가기 화면에는 목적지 안내가 섞이지 않는다")
    void lifecycle() throws Exception {
        HttpResponse<String> started = send("POST", "{\"latitude\":37.5665,\"longitude\":126.978,\"mode\":\"SHORTEST\"}", true);
        assertEquals(200, started.statusCode(), started.body());
        JsonNode body = json.readTree(started.body());
        assertTrue(body.get("active").asBoolean());
        assertEquals("DESTINATION", body.get("purpose").asString());
        assertEquals("SHORTEST", body.get("mode").asString());
        assertEquals("WAITING_FOR_ROBOT_LOCATION", body.get("status").asString());

        HttpResponse<String> owner = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/owner/return"))
                .header("Authorization", "Bearer " + OPERATOR_TOKEN).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertFalse(json.readTree(owner.body()).get("active").asBoolean());

        assertEquals("DESTINATION", json.readTree(send("GET", null, true).body()).get("purpose").asString());
        JsonNode stopped = json.readTree(send("DELETE", null, true).body());
        assertFalse(stopped.get("active").asBoolean());
        assertEquals("IDLE", stopped.get("status").asString());
    }

    @Test
    @DisplayName("잘못된 목적지·방식은 400, 토큰이 없으면 401")
    void badRequests() throws Exception {
        assertEquals(400, send("POST", "{\"latitude\":95,\"longitude\":126.978}", true).statusCode());
        assertEquals(400, send("POST", "{\"latitude\":37.5,\"longitude\":127,\"mode\":\"FASTEST\"}", true).statusCode());
        assertEquals(400, send("POST", "", true).statusCode());
        assertEquals(401, send("POST", "{\"latitude\":37.5,\"longitude\":127}", false).statusCode());
        assertEquals(401, send("DELETE", null, false).statusCode());
    }

    private HttpResponse<String> send(String method, String body, boolean withToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/navigation"))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (withToken) {
            request.header("Authorization", "Bearer " + OPERATOR_TOKEN);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
