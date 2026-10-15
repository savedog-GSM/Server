package com.example.savedog.global.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

// 브라우저에서 API와 실시간 접속을 쓸 수 있는 출처. REST(CORS)와 STOMP가 같은 목록을 쓴다.
@Slf4j
@Component
public class AllowedOrigins {
    private final String[] origins;

    public AllowedOrigins(@Value("${savedog.cors.allowed-origins}") String configured) {
        // "a, b"처럼 쉼표 뒤에 공백을 넣어도 맞게 읽는다.
        this.origins = Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (Arrays.asList(origins).contains("*")) {
            log.warn("CORS_ALLOWED_ORIGINS에 *가 들어 있어 모든 웹사이트가 이 서버의 API를 부를 수 있습니다.");
        }
    }

    public String[] values() {
        return origins.clone();
    }
}
