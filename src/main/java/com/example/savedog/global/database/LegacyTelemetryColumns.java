package com.example.savedog.global.database;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

/**
 * 예전 버전이 만든 telemetry 테이블에는 기울기 컬럼(roll, pitch)이 NOT NULL로 남아 있다. (이슈 #5)
 * 이제 기울기는 저장하지 않으므로 그대로 두면 센서 값 저장이 모두 실패한다.
 * ddl-auto=update는 기존 컬럼의 제약을 바꾸지 않으므로, 서버가 뜰 때 한 번 NULL 허용으로 바꾼다.
 * 컬럼과 그 안의 예전 값은 지우지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LegacyTelemetryColumns implements SmartInitializingSingleton {
    private static final String TABLE = "telemetry";
    private static final List<String> LEGACY_COLUMNS = List.of("roll", "pitch");

    private final DataSource dataSource;

    // 모든 빈이 만들어진 뒤(ddl-auto가 테이블을 고친 뒤), 웹 서버가 요청을 받기 전에 불린다.
    // 서버가 뜨자마자 들어온 센서 값이 예전 제약에 걸려 실패하지 않게 하려는 것이다.
    @Override
    public void afterSingletonsInstantiated() {
        relax();
    }

    void relax() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String alterFormat = alterFormatFor(metaData.getDatabaseProductName());
            if (alterFormat == null) {
                return;
            }
            for (String column : LEGACY_COLUMNS) {
                if (isNotNull(connection, metaData, column)) {
                    try (Statement statement = connection.createStatement()) {
                        // 컬럼 이름은 위의 고정 목록에서만 온다.
                        statement.execute(alterFormat.formatted(TABLE, column));
                    }
                    log.info("예전 기울기 컬럼 {}.{}를 비어 있어도 되게 바꿨습니다.", TABLE, column);
                }
            }
        } catch (SQLException e) {
            // 서버는 계속 띄운다. 센서 값 저장이 실패하면 아래 SQL을 직접 실행하면 된다.
            log.warn("예전 기울기 컬럼을 정리하지 못했습니다. 센서 값 저장이 실패하면 "
                    + "ALTER TABLE telemetry MODIFY roll DOUBLE NULL, MODIFY pitch DOUBLE NULL; 을 실행하세요.", e);
        }
    }

    // 제약을 바꾸는 문법이 DB마다 다르다. 모르는 DB면 건드리지 않는다.
    private String alterFormatFor(String productName) {
        String product = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        if (product.contains("mysql") || product.contains("mariadb")) {
            return "ALTER TABLE %s MODIFY COLUMN %s DOUBLE NULL";
        }
        if (product.contains("h2")) {
            return "ALTER TABLE %s ALTER COLUMN %s SET NULL";
        }
        return null;
    }

    private boolean isNotNull(Connection connection, DatabaseMetaData metaData, String column) throws SQLException {
        try (ResultSet columns = metaData.getColumns(
                connection.getCatalog(), null, identifier(metaData, TABLE), identifier(metaData, column))) {
            return columns.next() && columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
        }
    }

    // DB가 이름을 대문자나 소문자로 바꿔 저장하면 같은 방식으로 찾는다.
    private String identifier(DatabaseMetaData metaData, String name) throws SQLException {
        if (metaData.storesUpperCaseIdentifiers()) {
            return name.toUpperCase(Locale.ROOT);
        }
        if (metaData.storesLowerCaseIdentifiers()) {
            return name.toLowerCase(Locale.ROOT);
        }
        return name;
    }
}
