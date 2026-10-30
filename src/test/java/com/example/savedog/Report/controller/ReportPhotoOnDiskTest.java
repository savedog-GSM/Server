package com.example.savedog.Report.controller;

import com.example.savedog.Vision.service.VisionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// S3 없이 서버 디스크만으로 폰 사진이 화면과 위험 신고까지 가는 흐름을 실제 HTTP로 확인한다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:report-photo-disk;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=" + ReportPhotoOnDiskTest.DEVICE_TOKEN,
                "savedog.security.operator-token=" + ReportPhotoOnDiskTest.OPERATOR_TOKEN,
                "savedog.public-base-url=https://server.test",
                // 저장 폴더가 있으면 S3 설정이 있어도 디스크를 쓴다.
                "savedog.s3.bucket=bucket-without-permission"
        }
)
@ActiveProfiles("test")
class ReportPhotoOnDiskTest {
    static final String DEVICE_TOKEN = "device-token-for-photo-on-disk";
    static final String OPERATOR_TOKEN = "operator-token-for-photo-on-disk";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @TempDir
    static Path imageDir;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("savedog.storage.local-dir", () -> imageDir.toString());
    }

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private VisionService visionService;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("폰 사진을 저장해 화면에 내주고, 위험이 5초 이어져 만든 신고에도 그 사진이 붙는다")
    void frameBecomesReportPhoto() throws Exception {
        location();
        assertEquals(204, send(HttpRequest.newBuilder(uri("/api/robot/frames"))
                .header("Content-Type", "image/jpeg")
                .POST(HttpRequest.BodyPublishers.ofByteArray(JPEG))).statusCode());

        assertArrayEquals(JPEG, imageAt(urlFrom("/api/robot/frames/latest/url")));

        // 18cm(위험)를 5초 넘게 보내면 서버가 신고를 만들면서 최근 사진을 복사해 붙인다.
        JsonNode report = null;
        for (int i = 0; i < 7 && report == null; i++) {
            location();
            arduino(18);
            Thread.sleep(1_000);
            JsonNode reports = json.readTree(operatorGet("/api/reports").body());
            if (!reports.isEmpty()) {
                report = reports.get(0);
            }
        }
        assertTrue(report != null, "신고가 만들어지지 않았습니다.");

        long id = report.get("id").asLong();
        for (int i = 0; i < 30 && !report.get("hasPhoto").asBoolean(); i++) {
            Thread.sleep(100);
            report = json.readTree(operatorGet("/api/reports/" + id).body());
        }
        assertTrue(report.get("hasPhoto").asBoolean(), report.toString());
        assertArrayEquals(JPEG, imageAt(urlFrom("/api/reports/" + id + "/photo/url")));
    }

    @Test
    @DisplayName("서명이 없거나 틀리면 이미지를 주지 않는다")
    void imageNeedsSignature() throws Exception {
        assertEquals(400, send(HttpRequest.newBuilder(uri("/api/images?key=frames/latest.jpg"))).statusCode());
        assertEquals(401, send(HttpRequest.newBuilder(
                uri("/api/images?key=frames/latest.jpg&expires=9999999999999&signature=forged"))).statusCode());
        assertEquals(401, send(HttpRequest.newBuilder(
                uri("/api/images?key=../../etc/passwd&expires=9999999999999&signature=forged"))).statusCode());
    }

    private void location() throws Exception {
        assertEquals(204, send(HttpRequest.newBuilder(uri("/api/robot/location"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"latitude\":37.5,\"longitude\":127.0,\"accuracy\":5}"))).statusCode());
    }

    private void arduino(double distance) throws Exception {
        assertEquals(201, send(HttpRequest.newBuilder(uri("/api/arduino/sensors"))
                .header("Content-Type", "application/json")
                .header("X-Device-Token", DEVICE_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString("{\"distance\":" + distance + "}"))).statusCode());
    }

    private URI urlFrom(String path) throws Exception {
        HttpResponse<String> response = operatorGet(path);
        assertEquals(200, response.statusCode(), response.body());
        URI url = URI.create(json.readTree(response.body()).get("url").asString());
        assertEquals("server.test", url.getHost());
        return url;
    }

    private byte[] imageAt(URI url) throws Exception {
        HttpResponse<byte[]> response = http.send(
                HttpRequest.newBuilder(uri(url.getRawPath() + "?" + url.getRawQuery())).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private HttpResponse<String> operatorGet(String path) throws Exception {
        return send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + OPERATOR_TOKEN));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String pathAndQuery) {
        return URI.create("http://localhost:" + port + pathAndQuery);
    }
}
