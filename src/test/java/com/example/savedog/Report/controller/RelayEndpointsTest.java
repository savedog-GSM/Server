package com.example.savedog.Report.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 사용자의 중계 프로그램(Notion "로보독 중계 프로그램")이 부르는 주소를 실제 HTTP로 확인한다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:relay-endpoints;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "savedog.security.device-token=" + RelayEndpointsTest.DEVICE_TOKEN,
                "savedog.security.operator-token=" + RelayEndpointsTest.OPERATOR_TOKEN,
                "savedog.report.mock-authority=true"
        }
)
@ActiveProfiles("test")
class RelayEndpointsTest {
    static final String DEVICE_TOKEN = "device-token-for-relay-test";
    static final String OPERATOR_TOKEN = "operator-token-for-relay-test";
    private static final String BOUNDARY = "----savedog-test-boundary";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private DataSource dataSource;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    @DisplayName("위험 신고 업로드: 장치 토큰이 있어야 하고, 새 신고는 201, 같은 위험을 다시 올리면 그 신고를 200으로 준다")
    void hazardUploadCreatesOnceAndNeedsDeviceToken() throws Exception {
        phoneAt(37.5001, 127.0001);
        Map<String, String> fields = fields("FIRE", "fire");

        assertEquals(401, upload(fields, JPEG, Map.of()).statusCode());
        assertEquals(401, upload(fields, JPEG, Map.of("Authorization", "Bearer " + OPERATOR_TOKEN)).statusCode());

        HttpResponse<String> created = upload(fields, JPEG, device());
        assertEquals(201, created.statusCode(), created.body());
        JsonNode report = json.readTree(created.body());
        assertEquals("FIRE", report.get("hazardType").asString());
        assertEquals("PENDING", report.get("status").asString());
        assertEquals(37.5001, report.get("latitude").asDouble());
        assertEquals("화재 (QR fire)", report.get("risk").get("reason").asString());
        // 테스트에는 S3가 없으므로 사진 없이 만들어진다.
        assertFalse(report.get("hasPhoto").asBoolean());

        HttpResponse<String> again = upload(fields, JPEG, device());
        assertEquals(200, again.statusCode(), again.body());
        assertEquals(report.get("id").asLong(), json.readTree(again.body()).get("id").asLong());

        HttpResponse<String> listed = send(get("/api/reports").header("Authorization", "Bearer " + OPERATOR_TOKEN));
        assertTrue(listed.body().contains("\"id\":" + report.get("id").asLong()), listed.body());
    }

    @Test
    @DisplayName("모의 창구를 켜면 접수번호 없이 제출해도 MOCK- 접수번호로 제출된다")
    void mockAuthorityIssuesReceipt() throws Exception {
        phoneAt(37.6001, 127.1001);
        HttpResponse<String> created = upload(fields("GROUND_DAMAGE", "ground"), null, device());
        assertEquals(201, created.statusCode(), created.body());
        long id = json.readTree(created.body()).get("id").asLong();

        HttpResponse<String> submitted = send(post("/api/reports/" + id + "/submit", "")
                .header("Authorization", "Bearer " + OPERATOR_TOKEN));

        assertEquals(200, submitted.statusCode(), submitted.body());
        JsonNode report = json.readTree(submitted.body());
        assertEquals("SUBMITTED", report.get("status").asString());
        assertEquals("ROUGH_ROAD", report.get("hazardType").asString());
        assertTrue(report.get("receiptNumber").asString().startsWith("MOCK-"), submitted.body());
    }

    @Test
    @DisplayName("위험 신고 업로드의 잘못된 요청: 모르는 유형 400, JSON 415, 깨진 multipart 400, 2MB 넘는 사진 413")
    void hazardUploadRejectsBadRequests() throws Exception {
        phoneAt(37.7001, 127.2001);

        HttpResponse<String> unknown = upload(fields("FLOOD", null), JPEG, device());
        assertEquals(400, unknown.statusCode(), unknown.body());
        assertTrue(unknown.body().contains("GENERAL_DANGER, FIRE, GROUND_DAMAGE"), unknown.body());

        HttpResponse<String> asJson = send(post("/api/robot/hazards", "{\"hazardType\":\"FIRE\"}")
                .header("Content-Type", "application/json").header("X-Device-Token", DEVICE_TOKEN));
        assertEquals(415, asJson.statusCode(), asJson.body());

        HttpResponse<String> broken = send(HttpRequest.newBuilder(uri("/api/robot/hazards"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("X-Device-Token", DEVICE_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString("--" + BOUNDARY + "\r\nthis is not a part")));
        assertEquals(400, broken.statusCode(), broken.body());

        // 2MB를 조금 넘는 사진: 요청 크기 한도(2MB+64KB) 안이지만 사진 한도(2MB)에 걸린다.
        byte[] slightlyTooBig = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, slightlyTooBig, 0, JPEG.length);
        HttpResponse<String> tooBig = upload(fields("FIRE", null), slightlyTooBig, device());
        assertEquals(413, tooBig.statusCode(), tooBig.body());
        assertTrue(tooBig.body().contains("PAYLOAD_TOO_LARGE"), tooBig.body());
        // 요청 자체가 한도를 넘는 경우는 ApiRequestGuardFilterTest에서 확인한다.
        // (서버가 본문을 읽기 전에 끊으므로 HTTP 클라이언트 쪽에서 응답 대신 연결 오류가 날 수 있다)
    }

    @Test
    @DisplayName("최근 위치가 없으면 409 LOCATION_UNAVAILABLE")
    void hazardUploadNeedsLocation() throws Exception {
        // 다른 테스트가 보낸 위치가 남아 있을 수 있으므로 61초 지난 것처럼 만들 수는 없다.
        // 대신 오차가 30m를 넘는 위치(쓰지 않는 위치)를 마지막으로 보낸다.
        send(post("/api/robot/location", "{\"latitude\":37.8,\"longitude\":127.3,\"accuracy\":500}")
                .header("Content-Type", "application/json").header("X-Device-Token", DEVICE_TOKEN));

        HttpResponse<String> response = upload(fields("FIRE", null), JPEG, device());

        assertEquals(409, response.statusCode(), response.body());
        assertTrue(response.body().contains("LOCATION_UNAVAILABLE"), response.body());
    }

    @Test
    @DisplayName("QR 이벤트: 장치 토큰으로 JSON 객체를 보내면 204, 비었거나 배열이면 400")
    void eventsEndpoint() throws Exception {
        String event = "{\"robotId\":\"robodog-01\",\"qrCode\":\"left\",\"source\":\"QR_DEMO\"}";

        assertEquals(401, send(jsonPost("/api/robot/events", event, Map.of())).statusCode());
        assertEquals(204, send(jsonPost("/api/robot/events", event, device())).statusCode());
        assertEquals(400, send(jsonPost("/api/robot/events", "{}", device())).statusCode());
        assertEquals(400, send(jsonPost("/api/robot/events", "[1,2]", device())).statusCode());
    }

    @Test
    @DisplayName("새 명령: EMERGENCY_STOP·RESUME·degrees를 받아들이고(로보독이 없으면 409), 잘못된 각도는 400")
    void newCommandsAreAccepted() throws Exception {
        Map<String, String> operator = Map.of("Authorization", "Bearer " + OPERATOR_TOKEN);

        HttpResponse<String> stop = send(jsonPost("/api/robot/commands", "{\"type\":\"EMERGENCY_STOP\"}", operator));
        assertEquals(409, stop.statusCode(), stop.body());
        assertTrue(stop.body().contains("ROBOT_NOT_CONNECTED"), stop.body());
        assertEquals(409, send(jsonPost("/api/robot/commands", "{\"type\":\"RESUME\",\"speed\":40}", operator)).statusCode());
        assertEquals(409, send(jsonPost("/api/robot/commands", "{\"type\":\"TURN_LEFT\",\"degrees\":90}", operator)).statusCode());

        assertEquals(400, send(jsonPost("/api/robot/commands", "{\"type\":\"TURN_LEFT\",\"degrees\":400}", operator)).statusCode());
        assertEquals(400, send(jsonPost("/api/robot/commands", "{\"type\":\"JUMP\"}", operator)).statusCode());
    }

    @Test
    @DisplayName("새로 만든 DB의 신고 종류 컬럼은 문자열이다 (종류가 늘어도 테이블을 고칠 필요가 없다)")
    void hazardTypeColumnIsText() {
        String type = new JdbcTemplate(dataSource).queryForObject("SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'HAZARD_REPORT' AND COLUMN_NAME = 'HAZARD_TYPE'", String.class);
        assertEquals("CHARACTER VARYING", type);
    }

    private void phoneAt(double latitude, double longitude) throws Exception {
        HttpResponse<String> response = send(jsonPost("/api/robot/location",
                "{\"latitude\":%s,\"longitude\":%s,\"accuracy\":5}".formatted(latitude, longitude), device()));
        assertEquals(204, response.statusCode(), response.body());
    }

    private Map<String, String> fields(String hazardType, String qrCode) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("robotId", "robodog-01");
        fields.put("hazardType", hazardType);
        fields.put("detectedAt", "2026-10-20T17:00:00+09:00");
        fields.put("source", "QR_DEMO");
        if (qrCode != null) {
            fields.put("qrCode", qrCode);
        }
        fields.put("roll", "1.5");
        return fields;
    }

    private HttpResponse<String> upload(Map<String, String> fields, byte[] image, Map<String, String> headers) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                    + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        if (image != null) {
            body.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"image\"; filename=\"hazard.jpg\""
                    + "\r\nContent-Type: image/jpeg\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.writeBytes(image);
            body.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest.Builder request = HttpRequest.newBuilder(uri("/api/robot/hazards"))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        headers.forEach(request::header);
        return send(request);
    }

    private Map<String, String> device() {
        return Map.of("X-Device-Token", DEVICE_TOKEN);
    }

    private HttpRequest.Builder jsonPost(String path, String body, Map<String, String> headers) {
        HttpRequest.Builder request = post(path, body).header("Content-Type", "application/json");
        headers.forEach(request::header);
        return request;
    }

    private HttpRequest.Builder post(String path, String body) {
        return HttpRequest.newBuilder(uri(path)).POST(HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
