package com.example.savedog.global.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

// 브라우저에서 API와 실시간 접속을 쓸 수 있는 출처. REST(CORS)와 STOMP가 같은 목록을 쓴다.
@Slf4j
@Component
public class AllowedOrigins {
    static final String DEFAULT_ORIGINS = "http://localhost:3000,http://localhost:5173";

    private final String[] origins;

    public AllowedOrigins(@Value("${savedog.cors.allowed-origins:" + DEFAULT_ORIGINS + "}") String configured) {
        // CORS_ALLOWED_ORIGIN=처럼 값 없이 적어 두면 모든 출처가 막히므로 기본값으로 되돌린다.
        if (configured == null || configured.isBlank()) {
            log.warn("CORS_ALLOWED_ORIGIN이 비어 있어 기본 출처({})만 허용합니다.", DEFAULT_ORIGINS);
            configured = DEFAULT_ORIGINS;
        }
        // "a, b"처럼 쉼표 뒤에 공백을 넣어도 맞게 읽는다.
        this.origins = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (Arrays.asList(origins).contains("*")) {
            log.warn("CORS_ALLOWED_ORIGIN에 *가 들어 있어 모든 웹사이트가 이 서버의 API를 부를 수 있습니다.");
        }
    }

    public String[] values() {
        return origins.clone();
    }
}
