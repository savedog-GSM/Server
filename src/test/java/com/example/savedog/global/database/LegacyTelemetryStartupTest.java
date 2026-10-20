package com.example.savedog.global.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 기울기 컬럼이 NOT NULL인 예전 DB로 서버를 띄웠을 때를 그대로 재현한다. (이슈 #5)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:legacy-startup;MODE=MySQL;DB_CLOSE_DELAY=-1;"
                        + "INIT=RUNSCRIPT FROM 'classpath:legacy-telemetry.sql'",
                "spring.jpa.hibernate.ddl-auto=update"
        }
)
@ActiveProfiles("test")
class LegacyTelemetryStartupTest {
    @Value("${local.server.port}")
    private int port;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ColumnsAtServerStart columnsAtServerStart;

    @Test
    @DisplayName("웹 서버가 뜬 시점에는 이미 기울기 컬럼이 풀려 있어서, 뜨자마자 온 센서 값도 저장된다")
    void relaxedBeforeServerAcceptsRequests() {
        assertEquals("PITCH=YES, ROLL=YES", columnsAtServerStart.nullability);
    }

    @Test
    @DisplayName("예전 테이블에 거리만 담은 센서 값이 저장되고, 예전 기록도 그대로 읽힌다")
    void savesDistanceOnlyReadingIntoLegacyTable() throws Exception {
        HttpResponse<String> saved = send(HttpRequest.newBuilder(uri("/api/robot/telemetry"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"sensor\":{\"distance\":42}}")));
        assertEquals(201, saved.statusCode(), saved.body());

        HttpResponse<String> track = send(HttpRequest.newBuilder(uri("/api/robot/telemetry")).GET());
        assertEquals(200, track.statusCode(), track.body());
        JsonNode old = null;
        for (JsonNode reading : JsonMapper.builder().build().readTree(track.body())) {
            if (reading.get("id").asLong() == 1) {
                old = reading;
            }
        }
        assertTrue(old != null, track.body());
        assertEquals(150.0, old.get("sensor").get("distance").asDouble());

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 예전 기울기 값은 지우지 않고, 새 기록의 기울기는 비어 있다.
        assertEquals(1.5, jdbc.queryForObject("SELECT roll FROM telemetry WHERE id = 1", Double.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM telemetry WHERE distance = 42 AND roll IS NULL AND pitch IS NULL", Integer.class));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class Probe {
        @Bean
        ColumnsAtServerStart columnsAtServerStart(DataSource dataSource) {
            return new ColumnsAtServerStart(new JdbcTemplate(dataSource));
        }
    }

    // 웹 서버가 막 떠서 요청을 받기 시작한 순간의 기울기 컬럼 상태를 적어 둔다.
    static class ColumnsAtServerStart implements ApplicationListener<WebServerInitializedEvent> {
        private final JdbcTemplate jdbc;
        private volatile String nullability;

        ColumnsAtServerStart(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public void onApplicationEvent(WebServerInitializedEvent event) {
            nullability = String.join(", ", jdbc.query(
                    "SELECT COLUMN_NAME, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_NAME = 'TELEMETRY' AND COLUMN_NAME IN ('ROLL', 'PITCH') ORDER BY COLUMN_NAME",
                    (row, index) -> row.getString(1) + "=" + row.getString(2)
            ));
        }
    }
}
