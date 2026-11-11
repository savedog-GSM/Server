package com.example.savedog.Owner.controller;

import com.example.savedog.Owner.face.FaceDetection;
import com.example.savedog.Owner.face.FaceEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// 얼굴 등록·삭제를 실제 HTTP로 확인한다. 얼굴 모델은 가짜로 바꿔 사진 속 얼굴 수와 특징값을 정한다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:owner-face;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=device-token-for-owner-face",
                "savedog.security.operator-token=" + OwnerFaceApiTest.OPERATOR_TOKEN
        }
)
@ActiveProfiles("test")
class OwnerFaceApiTest {
    static final String OPERATOR_TOKEN = "operator-token-for-owner-face";
    private static final String BOUNDARY = "----owner-face-boundary";
    private static final FaceDetection FACE = new FaceDetection(0.95, 100, 80, 150, 180, new double[10]);

    @Value("${local.server.port}")
    private int port;

    @MockitoBean
    private FaceEngine faceEngine;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void reset() throws Exception {
        when(faceEngine.available()).thenReturn(true);
        when(faceEngine.detect(any())).thenReturn(List.of(FACE));
        when(faceEngine.embed(any(), any())).thenReturn(owner());
        delete("").statusCode();
    }

    @Test
    @DisplayName("주인 얼굴을 올리면 특징값만 저장하고(201), 같은 사람 사진을 더 올리면 기존 얼굴과의 유사도를 알려 준다")
    void registersOwner() throws Exception {
        HttpResponse<String> first = upload(photo());
        assertEquals(201, first.statusCode(), first.body());
        JsonNode body = json.readTree(first.body());
        assertEquals(1, body.get("registeredCount").asInt());
        assertTrue(body.get("similarityToOthers").isNull());
        assertEquals(0.95, body.get("faceScore").asDouble());

        HttpResponse<String> second = upload(photo());
        assertEquals(201, second.statusCode(), second.body());
        assertEquals(2, json.readTree(second.body()).get("registeredCount").asInt());
        assertEquals(1.0, json.readTree(second.body()).get("similarityToOthers").asDouble());
    }

    @Test
    @DisplayName("폰 사진 크기(1MB 안팎)도 받는다")
    void acceptsRealisticPhotoSize() throws Exception {
        BufferedImage noisy = new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(1);
        for (int y = 0; y < noisy.getHeight(); y++) {
            for (int x = 0; x < noisy.getWidth(); x++) {
                noisy.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(noisy, "jpg", out);
        assertTrue(out.size() > 500 * 1024 && out.size() < 2 * 1024 * 1024, "사진 크기 " + out.size());

        HttpResponse<String> response = upload(out.toByteArray());

        assertEquals(201, response.statusCode(), response.body());
    }

    @Test
    @DisplayName("이미 등록된 얼굴과 다른 사람이면 받지 않는다 (400)")
    void rejectsDifferentPerson() throws Exception {
        assertEquals(201, upload(photo()).statusCode());
        when(faceEngine.embed(any(), any())).thenReturn(stranger());

        HttpResponse<String> response = upload(photo());

        assertEquals(400, response.statusCode(), response.body());
        assertTrue(response.body().contains("다른 사람"), response.body());
    }

    @Test
    @DisplayName("얼굴이 없거나 여러 명이거나 너무 작으면 400, 사진이 아니거나 빠졌으면 400")
    void rejectsBadPhotos() throws Exception {
        when(faceEngine.detect(any())).thenReturn(List.of());
        HttpResponse<String> none = upload(photo());
        assertEquals(400, none.statusCode());
        assertTrue(none.body().contains("FACE_NOT_FOUND"), none.body());

        when(faceEngine.detect(any())).thenReturn(List.of(FACE, FACE));
        assertTrue(upload(photo()).body().contains("2개"));

        when(faceEngine.detect(any())).thenReturn(List.of(new FaceDetection(0.95, 10, 10, 40, 50, new double[10])));
        assertTrue(upload(photo()).body().contains("너무 작게"));

        assertEquals(400, upload("not an image".getBytes(StandardCharsets.UTF_8)).statusCode());
        assertEquals(400, send(multipart("other", photo())).statusCode());
    }

    @Test
    @DisplayName("10장까지만 등록하고(409), 하나만 지우거나(?id=) 전부 지울 수 있다. 없는 id는 404")
    void limitAndDelete() throws Exception {
        long firstId = 0;
        for (int i = 0; i < 10; i++) {
            HttpResponse<String> response = upload(photo());
            assertEquals(201, response.statusCode(), response.body());
            if (i == 0) {
                firstId = json.readTree(response.body()).get("id").asLong();
            }
        }
        HttpResponse<String> over = upload(photo());
        assertEquals(409, over.statusCode(), over.body());

        HttpResponse<String> one = delete("?id=" + firstId);
        assertEquals(200, one.statusCode(), one.body());
        assertEquals(9, json.readTree(one.body()).get("remaining").asInt());
        assertEquals(404, delete("?id=" + firstId).statusCode());

        HttpResponse<String> all = delete("");
        assertEquals(9, json.readTree(all.body()).get("deleted").asInt());
        assertEquals(0, json.readTree(all.body()).get("remaining").asInt());
    }

    @Test
    @DisplayName("관제 토큰이 있어야 하고, 얼굴 모델이 없으면 503")
    void authAndModelMissing() throws Exception {
        HttpResponse<String> noToken = http.send(multipart("image", photo()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, noToken.statusCode());

        when(faceEngine.available()).thenReturn(false);
        HttpResponse<String> unavailable = upload(photo());
        assertEquals(503, unavailable.statusCode(), unavailable.body());
        assertTrue(unavailable.body().contains("FACE_MODEL_UNAVAILABLE"), unavailable.body());
    }

    private static float[] owner() {
        float[] vector = new float[128];
        vector[0] = 1;
        return vector;
    }

    private static float[] stranger() {
        float[] vector = new float[128];
        vector[1] = 1;
        return vector;
    }

    private static byte[] photo() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB), "jpg", out);
        return out.toByteArray();
    }

    private HttpResponse<String> upload(byte[] image) throws Exception {
        return send(multipart("image", image));
    }

    private HttpRequest.Builder multipart(String field, byte[] image) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + field
                + "\"; filename=\"face.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(image);
        body.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(uri(""))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
    }

    private HttpResponse<String> delete(String query) throws Exception {
        return send(HttpRequest.newBuilder(uri(query)).DELETE());
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.header("Authorization", "Bearer " + OPERATOR_TOKEN).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String query) {
        return URI.create("http://localhost:" + port + "/api/owner/faces" + query);
    }
}
