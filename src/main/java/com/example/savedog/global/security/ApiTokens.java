package com.example.savedog.global.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * API를 부를 수 있는 쪽을 가르는 공유 토큰 두 개.
 * device: 로보독·스마트폰(센서 값, 위치, 프레임 전송과 로보독 명령 채널)
 * operator: 관제 화면(그 밖의 모든 API와 실시간 접속)
 * 토큰을 설정하지 않으면 그 쪽은 검사하지 않는다. 개발할 때만 그렇게 쓴다.
 */
@Slf4j
@Component
public class ApiTokens {
    public static final String DEVICE_HEADER = "X-Device-Token";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MIN_LENGTH = 16;

    private final byte[] deviceToken;
    private final byte[] operatorToken;

    public ApiTokens(
            @Value("${savedog.security.device-token:}") String deviceToken,
            @Value("${savedog.security.operator-token:}") String operatorToken
    ) {
        this.deviceToken = prepare(deviceToken, "DEVICE_API_TOKEN", "로보독·스마트폰이 보내는 API와 로보독 명령 채널");
        this.operatorToken = prepare(operatorToken, "OPERATOR_API_TOKEN", "명령 전송을 포함한 관제용 API와 실시간 접속");
    }

    public boolean deviceRequired() {
        return deviceToken != null;
    }

    public boolean operatorRequired() {
        return operatorToken != null;
    }

    // X-Device-Token 헤더 값 그대로
    public boolean matchesDevice(String presented) {
        return matches(deviceToken, presented);
    }

    // "Authorization: Bearer {토큰}" 헤더 값 그대로
    public boolean matchesOperator(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return operatorToken == null;
        }
        return matches(operatorToken, authorizationHeader.substring(BEARER_PREFIX.length()).trim());
    }

    private boolean matches(byte[] expected, String presented) {
        if (expected == null) {
            return true;
        }
        if (presented == null) {
            return false;
        }
        // 틀린 위치에 따라 걸리는 시간이 달라지지 않게 비교한다.
        return MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] prepare(String token, String variable, String protects) {
        if (token == null || token.isBlank()) {
            log.warn("{}이 설정되지 않아 {}을(를) 누구나 사용할 수 있습니다. 외부에서 접속할 수 있는 환경이라면 반드시 설정하세요.",
                    variable, protects);
            return null;
        }
        if (token.trim().length() < MIN_LENGTH) {
            throw new IllegalStateException(variable + "은 " + MIN_LENGTH + "자 이상이어야 합니다. 짧은 토큰은 쉽게 맞힐 수 있습니다.");
        }
        return token.trim().getBytes(StandardCharsets.UTF_8);
    }
}
