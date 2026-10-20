package com.example.savedog.Report.service;

import com.example.savedog.Report.domain.HazardReport;
import com.example.savedog.Report.reporter.HazardReporter;
import com.example.savedog.Report.repository.HazardReportRepository;
import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 실제 HTTP 요청으로 제출해서, 창구를 기다리는 동안 DB 연결을 쥐고 있지 않은지 확인한다. (이슈 #6)
// open-in-view가 켜져 있으면 트랜잭션이 끝나도 요청이 끝날 때까지 연결을 붙잡는다.
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:report-submit;MODE=MySQL;DB_CLOSE_DELAY=-1"
)
@ActiveProfiles("test")
class ReportSubmitConnectionTest {
    @Value("${local.server.port}")
    private int port;

    @Autowired
    private HazardReportRepository reportRepository;

    @Autowired
    private RecordingReporter reporter;

    @Test
    @DisplayName("민원 창구를 기다리는 동안에는 DB 연결을 하나도 쥐고 있지 않는다")
    void holdsNoConnectionWhileWaitingForAuthority() throws Exception {
        HazardReport report = reportRepository.save(new HazardReport(
                null, HazardType.OBSTACLE, 37.5665, 126.978, null,
                "보행로에 장애물이 있습니다.", SafetyLevel.DANGER, 5, "전방 15cm 장애물"
        ));

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:%d/api/reports/%d/submit".formatted(port, report.getId())))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(1, reporter.calls);
        assertEquals(0, reporter.activeConnectionsDuringCall);
        assertEquals("R-77", reportRepository.findById(report.getId()).orElseThrow().getReceiptNumber());
    }

    @TestConfiguration
    static class Authority {
        @Bean
        @Primary
        RecordingReporter recordingReporter(DataSource dataSource) throws SQLException {
            return new RecordingReporter(dataSource.unwrap(HikariDataSource.class));
        }
    }

    // 창구 대신 불려서, 불린 순간 커넥션 풀에서 빌려 간 연결 수를 적어 둔다.
    static class RecordingReporter implements HazardReporter {
        private final HikariDataSource pool;
        private volatile int calls;
        private volatile int activeConnectionsDuringCall = -1;

        RecordingReporter(HikariDataSource pool) {
            this.pool = pool;
        }

        @Override
        public String submit(HazardReport report) {
            calls++;
            activeConnectionsDuringCall = pool.getHikariPoolMXBean().getActiveConnections();
            return "R-77";
        }
    }
}
