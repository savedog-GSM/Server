package com.example.savedog.global;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// docker-compose 헬스체크(curl -f /actuator/health)가 토큰을 켠 서버에서도 통과하는지 확인한다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:health-check;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=device-token-for-health-test",
                "savedog.security.operator-token=operator-token-for-health-test"
        }
)
@ActiveProfiles("test")
class HealthCheckTest {
    @Value("${local.server.port}")
    private int port;

    @Test
    @DisplayName("헬스체크는 토큰 없이 200 UP을 돌려준다")
    void healthIsOpen() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }

    @Test
    @DisplayName("health 말고 다른 actuator 정보는 열지 않는다")
    void otherEndpointsAreClosed() throws Exception {
        assertNotEquals(200, get("/actuator/env").statusCode());
        assertNotEquals(200, get("/actuator/beans").statusCode());
    }

    @Test
    @DisplayName("헬스체크를 열어도 API는 여전히 토큰을 요구한다")
    void apiStillNeedsToken() throws Exception {
        assertEquals(401, get("/api/robot/status").statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }
}
