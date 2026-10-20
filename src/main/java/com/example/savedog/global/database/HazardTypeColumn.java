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
import java.util.Locale;

/**
 * 예전 버전이 만든 hazard_report.hazard_type은 DB의 ENUM('ETC','OBSTACLE','ROUGH_ROAD') 타입이다.
 * 그대로 두면 새로 생긴 종류(FIRE)를 저장할 때 실패한다. ddl-auto=update는 기존 컬럼의 타입을 바꾸지 않으므로,
 * 서버가 뜰 때 한 번 문자열 타입으로 바꾼다. 들어 있던 값은 그대로 남는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HazardTypeColumn implements SmartInitializingSingleton {
    private static final String TABLE = "hazard_report";
    private static final String COLUMN = "hazard_type";

    private final DataSource dataSource;

    // 모든 빈이 만들어진 뒤(ddl-auto가 테이블을 고친 뒤), 웹 서버가 요청을 받기 전에 불린다.
    @Override
    public void afterSingletonsInstantiated() {
        widen();
    }

    void widen() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String alter = alterFor(metaData.getDatabaseProductName());
            if (alter == null || !isEnum(connection, metaData)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute(alter);
            }
            log.info("{}.{}를 ENUM에서 문자열 타입으로 바꿨습니다.", TABLE, COLUMN);
        } catch (SQLException e) {
            // 서버는 계속 띄운다. 새 종류(FIRE)의 신고 저장만 실패하므로 아래 SQL을 직접 실행하면 된다.
            log.warn("{}.{}를 문자열 타입으로 바꾸지 못했습니다. 화재 신고 저장이 실패하면 "
                    + "ALTER TABLE hazard_report MODIFY COLUMN hazard_type VARCHAR(32) NULL; 을 실행하세요.", TABLE, COLUMN, e);
        }
    }

    // 타입을 바꾸는 문법이 DB마다 다르다. 모르는 DB면 건드리지 않는다.
    private String alterFor(String productName) {
        String product = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        if (product.contains("mysql") || product.contains("mariadb")) {
            return "ALTER TABLE hazard_report MODIFY COLUMN hazard_type VARCHAR(32) NULL";
        }
        if (product.contains("h2")) {
            return "ALTER TABLE hazard_report ALTER COLUMN hazard_type SET DATA TYPE VARCHAR(32)";
        }
        return null;
    }

    private boolean isEnum(Connection connection, DatabaseMetaData metaData) throws SQLException {
        try (ResultSet columns = metaData.getColumns(
                connection.getCatalog(), null, identifier(metaData, TABLE), identifier(metaData, COLUMN))) {
            // MySQL 드라이버는 "ENUM", H2는 "ENUM('ETC', ...)"처럼 값 목록까지 붙여 준다.
            return columns.next() && String.valueOf(columns.getString("TYPE_NAME"))
                    .toUpperCase(Locale.ROOT).startsWith("ENUM");
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
