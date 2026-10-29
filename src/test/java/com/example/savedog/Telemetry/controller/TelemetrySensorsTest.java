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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 아두이노(초음파 + 온습도 센서)가 보내는 센서 값을 실제 HTTP로 확인한다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:telemetry-sensors;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=" + TelemetrySensorsTest.DEVICE_TOKEN,
                "savedog.security.operator-token=operator-token-for-sensor-test"
        }
)
@ActiveProfiles("test")
class TelemetrySensorsTest {
    static final String DEVICE_TOKEN = "device-token-for-sensor-test";

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("온습도를 같이 보내면 저장해서 돌려주고, 없으면 null이다")
    void storesTemperatureAndHumidity() throws Exception {
        HttpResponse<String> withClimate = send("{\"sensor\":{\"distance\":120.5,\"temperature\":24.3,\"humidity\":41}}");
        assertEquals(201, withClimate.statusCode(), withClimate.body());
        JsonNode sensor = json.readTree(withClimate.body()).get("sensor");
        assertEquals(120.5, sensor.get("distance").asDouble());
        assertEquals(24.3, sensor.get("temperature").asDouble());
        assertEquals(41.0, sensor.get("humidity").asDouble());

        HttpResponse<String> distanceOnly = send("{\"sensor\":{\"distance\":80}}");
        assertEquals(201, distanceOnly.statusCode(), distanceOnly.body());
        JsonNode plain = json.readTree(distanceOnly.body()).get("sensor");
        assertTrue(plain.get("temperature").isNull(), distanceOnly.body());
        assertTrue(plain.get("humidity").isNull(), distanceOnly.body());
    }

    @Test
    @DisplayName("온습도가 범위를 벗어나거나 숫자가 아니면 400, 거리 없이 온습도만 보내도 400")
    void rejectsBadClimateValues() throws Exception {
        assertBadRequest("{\"sensor\":{\"distance\":80,\"temperature\":200}}", "sensor.temperature");
        assertBadRequest("{\"sensor\":{\"distance\":80,\"temperature\":-41}}", "sensor.temperature");
        assertBadRequest("{\"sensor\":{\"distance\":80,\"humidity\":101}}", "sensor.humidity");
        assertBadRequest("{\"sensor\":{\"distance\":80,\"humidity\":-1}}", "sensor.humidity");
        assertBadRequest("{\"sensor\":{\"temperature\":24,\"humidity\":40}}", "sensor.distance");
        assertEquals(400, send("{\"sensor\":{\"distance\":80,\"temperature\":\"hot\"}}").statusCode());
    }

    @Test
    @DisplayName("아두이노 전용 주소: 감싸지 않은 값으로 받아 똑같이 저장하고, 토큰이 없으면 401, 값이 잘못되면 400")
    void arduinoEndpoint() throws Exception {
        HttpResponse<String> created = post("/api/arduino/sensors",
                "{\"distance\":55.5,\"temperature\":23.1,\"humidity\":38}", DEVICE_TOKEN);
        assertEquals(201, created.statusCode(), created.body());
        JsonNode sensor = json.readTree(created.body()).get("sensor");
        assertEquals(55.5, sensor.get("distance").asDouble());
        assertEquals(23.1, sensor.get("temperature").asDouble());
        assertEquals(38.0, sensor.get("humidity").asDouble());
        // 거리 55.5cm는 2점(안전)
        assertEquals(2, json.readTree(created.body()).get("risk").get("score").asInt());

        assertEquals(201, post("/api/arduino/sensors", "{\"distance\":300}", DEVICE_TOKEN).statusCode());
        assertEquals(401, post("/api/arduino/sensors", "{\"distance\":300}", null).statusCode());
        assertEquals(401, post("/api/arduino/sensors", "{\"distance\":300}", "wrong-token-wrong-token").statusCode());

        HttpResponse<String> noDistance = post("/api/arduino/sensors", "{\"temperature\":23}", DEVICE_TOKEN);
        assertEquals(400, noDistance.statusCode(), noDistance.body());
        assertTrue(noDistance.body().contains("distance 값"), noDistance.body());
        HttpResponse<String> hot = post("/api/arduino/sensors", "{\"distance\":30,\"temperature\":99}", DEVICE_TOKEN);
        assertEquals(400, hot.statusCode(), hot.body());
        assertTrue(hot.body().contains("temperature 값은 -40 이상 85 이하"), hot.body());
        assertEquals(400, post("/api/arduino/sensors", "", DEVICE_TOKEN).statusCode());
        assertEquals(400, post("/api/arduino/sensors", "not json", DEVICE_TOKEN).statusCode());
    }

    private void assertBadRequest(String body, String field) throws Exception {
        HttpResponse<String> response = send(body);
        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains(field), response.body());
    }

    private HttpResponse<String> send(String body) throws Exception {
        return post("/api/robot/telemetry", body, DEVICE_TOKEN);
    }

    private HttpResponse<String> post(String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) {
            request.header("X-Device-Token", token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
