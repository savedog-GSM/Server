package com.example.savedog.Telemetry.controller;

import com.example.savedog.Vision.service.VisionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// S3가 없을 때 폰 사진이 화면까지 가는 흐름을 실제 HTTP로 확인한다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:live-frame;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=device-token-for-live-frame",
                "savedog.security.operator-token=" + LiveFrameFlowTest.OPERATOR_TOKEN,
                "savedog.public-base-url=https://server.test/",
                "savedog.s3.bucket="
        }
)
@ActiveProfiles("test")
class LiveFrameFlowTest {
    static final String OPERATOR_TOKEN = "operator-token-for-live-frame";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private VisionService visionService;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("폰 사진 전송 → 관제 토큰으로 주소 조회 → 토큰 없이 그 주소로 이미지를 받는다. 서명이 틀리면 401")
    void phoneFrameReachesTheScreen() throws Exception {
        when(visionService.submit(any())).thenReturn(true);

        HttpResponse<String> sent = http.send(HttpRequest.newBuilder(uri("/api/robot/frames"))
                .header("Content-Type", "image/jpeg")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JPEG)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(204, sent.statusCode(), sent.body());

        HttpResponse<String> found = http.send(HttpRequest.newBuilder(uri("/api/robot/frames/latest/url"))
                .header("Authorization", "Bearer " + OPERATOR_TOKEN).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, found.statusCode(), found.body());
        URI url = URI.create(json.readTree(found.body()).get("url").asString());
        assertEquals("server.test", url.getHost());

        HttpResponse<byte[]> image = http.send(HttpRequest.newBuilder(uri(url.getPath() + "?" + url.getQuery())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, image.statusCode());
        assertArrayEquals(JPEG, image.body());
        assertEquals("image/jpeg", image.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(image.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));

        HttpResponse<String> forged = http.send(HttpRequest.newBuilder(
                        uri("/api/robot/frames/live?expires=9999999999999&signature=forged")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, forged.statusCode(), forged.body());
    }

    private URI uri(String pathAndQuery) {
        return URI.create("http://localhost:" + port + pathAndQuery);
    }
}
