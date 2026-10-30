package com.example.savedog.Telemetry.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 스마트폰 카메라가 토큰 없이 사진을 보내는 경로를 실제 HTTP로 확인한다.
// 테스트에는 S3와 물체 인식 모델이 없어서, 인증을 통과한 정상 사진은 이미지 저장소 없음(503)으로 끝난다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:phone-frame;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=device-token-for-phone-frame",
                "savedog.security.operator-token=operator-token-for-phone-frame",
                "savedog.cors.allowed-origins=" + PhoneFrameUploadTest.ORIGIN
        }
)
@ActiveProfiles("test")
class PhoneFrameUploadTest {
    static final String ORIGIN = "https://savedog.vercel.app";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @DisplayName("토큰 없이 사진을 받고, 형식 검사와 다른 API의 토큰 검사는 그대로다")
    void framesNeedNoToken() throws Exception {
        assertEquals(503, frame(JPEG, "image/jpeg").statusCode());
        assertEquals(400, frame("not a jpeg".getBytes(), "image/jpeg").statusCode());
        assertEquals(415, frame(JPEG, "image/png").statusCode());

        HttpResponse<String> latest = http.send(HttpRequest.newBuilder(uri("/api/robot/frames/latest/url")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(401, latest.statusCode(), latest.body());
    }

    @Test
    @DisplayName("브라우저 사전 요청(CORS)도 프론트 주소에서 통과한다")
    void corsPreflight() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/robot/frames"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type")
                .build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(), response.body());
        Optional<String> allowed = response.headers().firstValue("Access-Control-Allow-Origin");
        assertTrue(allowed.isPresent() && allowed.get().equals(ORIGIN), response.headers().toString());
    }

    private HttpResponse<String> frame(byte[] body, String contentType) throws Exception {
        return http.send(HttpRequest.newBuilder(uri("/api/robot/frames"))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
