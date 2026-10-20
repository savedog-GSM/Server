package com.example.savedog.global.database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyTelemetryColumnsTest {
    // 테스트마다 새 인메모리 DB를 쓴다.
    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:legacy-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""
    );
    private final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    private final LegacyTelemetryColumns migration = new LegacyTelemetryColumns(dataSource);

    @AfterEach
    void shutdown() {
        jdbc.execute("SHUTDOWN");
    }

    @Test
    @DisplayName("예전 테이블의 NOT NULL 기울기 컬럼을 풀어서 거리만으로 저장할 수 있게 한다")
    void relaxesLegacyTiltColumns() {
        jdbc.execute("CREATE TABLE telemetry (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "distance DOUBLE NOT NULL, roll DOUBLE NOT NULL, pitch DOUBLE NOT NULL, recorded_at TIMESTAMP)");
        jdbc.update("INSERT INTO telemetry (distance, roll, pitch, recorded_at) VALUES (150, 1.5, -2, CURRENT_TIMESTAMP)");
        // 고치기 전에는 지금 서버가 하는 저장(기울기 없이)이 실패한다.
        assertThrows(Exception.class, this::insertWithoutTilt);

        migration.relax();

        assertDoesNotThrow(this::insertWithoutTilt);
        // 예전 값은 그대로 남는다.
        assertEquals(1.5, jdbc.queryForObject("SELECT roll FROM telemetry WHERE id = 1", Double.class));
    }

    @Test
    @DisplayName("두 번 돌려도 문제없다")
    void runsTwiceSafely() {
        jdbc.execute("CREATE TABLE telemetry (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "distance DOUBLE NOT NULL, roll DOUBLE NOT NULL, pitch DOUBLE NOT NULL, recorded_at TIMESTAMP)");

        migration.relax();
        assertDoesNotThrow(() -> migration.relax());
        assertDoesNotThrow(this::insertWithoutTilt);
    }

    @Test
    @DisplayName("기울기 컬럼이 없는 새 테이블이나 테이블이 아예 없을 때는 아무것도 하지 않는다")
    void doesNothingWithoutLegacyColumns() {
        assertDoesNotThrow(() -> migration.relax());

        jdbc.execute("CREATE TABLE telemetry (id BIGINT AUTO_INCREMENT PRIMARY KEY, distance DOUBLE, recorded_at TIMESTAMP)");
        assertDoesNotThrow(() -> migration.relax());
        assertDoesNotThrow(this::insertWithoutTilt);
    }

    private void insertWithoutTilt() {
        jdbc.update("INSERT INTO telemetry (distance, recorded_at) VALUES (15, CURRENT_TIMESTAMP)");
    }
}
