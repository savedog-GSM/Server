package com.example.savedog.global.database;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 기록이 쌓일수록 느려지는 조회에 인덱스가 실제로 만들어지는지 확인한다. (이슈 #4)
@SpringBootTest
@ActiveProfiles("test")
class SchemaIndexTest {
    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("시간순·위치·상태 조회에 쓰는 인덱스가 만들어진다")
    void indexesExist() throws SQLException {
        assertEquals(List.of("recorded_at"), columnsOf("telemetry", "idx_telemetry_recorded_at"));
        assertEquals(List.of("assessed_at"), columnsOf("safety_record", "idx_safety_record_assessed_at"));
        assertEquals(List.of("latitude", "longitude"), columnsOf("safety_record", "idx_safety_record_location"));
        assertEquals(List.of("created_at"), columnsOf("hazard_report", "idx_hazard_report_created_at"));
        assertEquals(List.of("status", "created_at"), columnsOf("hazard_report", "idx_hazard_report_status_created_at"));
    }

    // 인덱스 이름별 컬럼 목록(인덱스 안의 순서대로). DB마다 대소문자 저장 방식이 달라 소문자로 맞춘다.
    private List<String> columnsOf(String table, String index) throws SQLException {
        Map<String, List<String>> indexes = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String tableName = metaData.storesUpperCaseIdentifiers() ? table.toUpperCase(Locale.ROOT) : table;
            try (ResultSet rows = metaData.getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
                while (rows.next()) {
                    String name = rows.getString("INDEX_NAME");
                    String column = rows.getString("COLUMN_NAME");
                    if (name != null && column != null) {
                        indexes.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> new ArrayList<>())
                                .add(column.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return indexes.getOrDefault(index, List.of());
    }
}
