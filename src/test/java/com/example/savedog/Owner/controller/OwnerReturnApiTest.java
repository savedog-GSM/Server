package com.example.savedog.Owner.controller;

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

// 주인 찾기 주소들을 실제 HTTP로 확인한다. (테스트에는 얼굴 모델·로보독·폰 위치가 없다)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:owner-return;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=device-token-for-owner-return",
                "savedog.security.operator-token=" + OwnerReturnApiTest.OPERATOR_TOKEN
        }
)
@ActiveProfiles("test")
class OwnerReturnApiTest {
    static final String OPERATOR_TOKEN = "operator-token-for-owner-return";

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("주인 보이는지: 모델이 없으면 available=false, 분석한 사진이 없으면 sighting=null")
    void sighting() throws Exception {
        HttpResponse<String> response = send("GET", "/api/owner/sighting", null, true);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertFalse(body.get("available").asBoolean());
        assertEquals(0, body.get("registeredFaces").asInt());
        assertFalse(body.get("ownerVisible").asBoolean());
        assertTrue(body.get("sighting").isNull());

        assertEquals(401, send("GET", "/api/owner/sighting", null, false).statusCode());
    }

    @Test
    @DisplayName("돌아가기: 시작하면 로보독 위치를 기다리고, 상태를 보고, 멈출 수 있다. 잘못된 위치는 400, 토큰이 없으면 401")
    void returnLifecycle() throws Exception {
        HttpResponse<String> started = send("POST", "/api/owner/return",
                "{\"latitude\":37.5665,\"longitude\":126.978,\"accuracy\":5}", true);
        assertEquals(200, started.statusCode(), started.body());
        JsonNode body = json.readTree(started.body());
        assertTrue(body.get("active").asBoolean());
        assertEquals("WAITING_FOR_ROBOT_LOCATION", body.get("status").asString());
        assertEquals(37.5665, body.get("owner").get("latitude").asDouble());

        assertEquals("WAITING_FOR_ROBOT_LOCATION",
                json.readTree(send("GET", "/api/owner/return", null, true).body()).get("status").asString());

        JsonNode stopped = json.readTree(send("DELETE", "/api/owner/return", null, true).body());
        assertFalse(stopped.get("active").asBoolean());
        assertEquals("IDLE", stopped.get("status").asString());

        assertEquals(400, send("POST", "/api/owner/return", "{\"latitude\":95,\"longitude\":126.978}", true).statusCode());
        assertEquals(400, send("POST", "/api/owner/return", "", true).statusCode());
        assertEquals(401, send("POST", "/api/owner/return", "{\"latitude\":37.5,\"longitude\":127}", false).statusCode());
    }

    private HttpResponse<String> send(String method, String path, String body, boolean withToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (withToken) {
            request.header("Authorization", "Bearer " + OPERATOR_TOKEN);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
