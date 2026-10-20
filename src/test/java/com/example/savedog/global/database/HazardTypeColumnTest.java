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

class HazardTypeColumnTest {
    // 테스트마다 새 인메모리 DB를 쓴다.
    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:hazard-type-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""
    );
    private final JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    private final HazardTypeColumn migration = new HazardTypeColumn(dataSource);

    @AfterEach
    void shutdown() {
        jdbc.execute("SHUTDOWN");
    }

    @Test
    @DisplayName("예전 ENUM 컬럼을 문자열로 바꿔 새 종류(FIRE)를 저장할 수 있게 하고, 들어 있던 값은 남긴다")
    void widensLegacyEnumColumn() {
        createLegacyTable();
        jdbc.update("INSERT INTO hazard_report (hazard_type) VALUES ('ROUGH_ROAD')");
        // 고치기 전에는 새 종류를 넣을 수 없다.
        assertThrows(Exception.class, this::insertFire);

        migration.widen();

        assertDoesNotThrow(this::insertFire);
        assertEquals("ROUGH_ROAD", jdbc.queryForObject("SELECT hazard_type FROM hazard_report WHERE id = 1", String.class));
        assertEquals("CHARACTER VARYING", typeOfColumn());
    }

    @Test
    @DisplayName("두 번 돌려도 문제없다")
    void runsTwiceSafely() {
        createLegacyTable();

        migration.widen();
        assertDoesNotThrow(migration::widen);
        assertDoesNotThrow(this::insertFire);
    }

    @Test
    @DisplayName("테이블이 없거나 이미 문자열 컬럼이면 아무것도 하지 않는다")
    void doesNothingOtherwise() {
        assertDoesNotThrow(migration::widen);

        jdbc.execute("CREATE TABLE hazard_report (id BIGINT AUTO_INCREMENT PRIMARY KEY, hazard_type VARCHAR(32))");
        assertDoesNotThrow(migration::widen);
        assertEquals("CHARACTER VARYING", typeOfColumn());
    }

    private void createLegacyTable() {
        jdbc.execute("CREATE TABLE hazard_report (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "hazard_type ENUM('ETC','OBSTACLE','ROUGH_ROAD'))");
    }

    private void insertFire() {
        jdbc.update("INSERT INTO hazard_report (hazard_type) VALUES ('FIRE')");
    }

    private String typeOfColumn() {
        return jdbc.queryForObject("SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_NAME = 'HAZARD_REPORT' AND COLUMN_NAME = 'HAZARD_TYPE'", String.class);
    }
}
