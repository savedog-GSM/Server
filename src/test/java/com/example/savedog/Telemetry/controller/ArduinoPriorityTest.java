package com.example.savedog.Telemetry.controller;

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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 아두이노와 중계 프로그램이 둘 다 거리를 보낼 때 아두이노 값만 쓰는지 실제 HTTP로 확인한다.
// 기다리는 시간을 줄이려고 우선 시간을 1초로 둔다. (실제 기본값은 3초)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:arduino-priority;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=" + ArduinoPriorityTest.DEVICE_TOKEN,
                "savedog.security.operator-token=" + ArduinoPriorityTest.OPERATOR_TOKEN,
                "savedog.telemetry.arduino-priority=1s"
        }
)
@ActiveProfiles("test")
class ArduinoPriorityTest {
    static final String DEVICE_TOKEN = "device-token-for-priority-test";
    static final String OPERATOR_TOKEN = "operator-token-for-priority-test";

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("아두이노가 보내는 동안 중계 프로그램 거리값은 저장하지 않고(202), 아두이노가 끊기면 다시 저장한다(201)")
    void arduinoWinsWhileActive() throws Exception {
        // 아두이노가 아직 안 보냈으면 로보독 센서 값을 쓴다.
        assertEquals(201, relay(111).statusCode());

        assertEquals(201, arduino(22).statusCode());
        HttpResponse<String> skipped = relay(99);
        assertEquals(202, skipped.statusCode(), skipped.body());
        assertTrue(skipped.body().isEmpty(), skipped.body());
        // 아두이노가 쓰는 중이면 로보독 쪽 값이 비어 있거나 잘못돼도 거절하지 않고 그냥 넘긴다.
        assertEquals(202, post("/api/robot/telemetry", "{\"sensor\":{\"roll\":1.5}}").statusCode());

        assertEquals(201, arduino(23).statusCode());
        assertEquals(202, relay(98).statusCode());

        // 저장된 기록: 로보독 111, 아두이노 22, 23. 중간의 99·98은 없다.
        assertEquals("[22.0, 23.0, 111.0]", savedDistances());

        // 아두이노가 1초 넘게 조용하면 다시 로보독 값을 쓴다.
        Thread.sleep(1_200);
        assertEquals(201, relay(97).statusCode());
        assertEquals("[22.0, 23.0, 97.0, 111.0]", savedDistances());
    }

    private HttpResponse<String> relay(double distance) throws Exception {
        return post("/api/robot/telemetry", "{\"sensor\":{\"distance\":%s}}".formatted(distance));
    }

    private HttpResponse<String> arduino(double distance) throws Exception {
        return post("/api/arduino/sensors", "{\"distance\":%s,\"temperature\":24,\"humidity\":40}".formatted(distance));
    }

    // 저장된 거리값을 크기 순서로 (조회 순서와 상관없이 비교하려고)
    private String savedDistances() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/robot/telemetry"))
                .header("Authorization", "Bearer " + OPERATOR_TOKEN).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        List<Double> distances = new ArrayList<>();
        for (JsonNode record : json.readTree(response.body())) {
            distances.add(record.get("sensor").get("distance").asDouble());
        }
        distances.sort(null);
        return distances.toString();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("X-Device-Token", DEVICE_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
