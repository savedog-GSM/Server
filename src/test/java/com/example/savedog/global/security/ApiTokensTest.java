package com.example.savedog.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTokensTest {
    private static final String DEVICE = "device-token-0123456789";
    private static final String OPERATOR = "operator-token-0123456789";

    @Test
    @DisplayName("토큰을 설정하지 않으면 검사하지 않는다")
    void unsetTokensAllowEveryone() {
        ApiTokens tokens = new ApiTokens("", "  ");

        assertFalse(tokens.deviceRequired());
        assertFalse(tokens.operatorRequired());
        assertTrue(tokens.matchesDevice(null));
        assertTrue(tokens.matchesOperator(null));
        assertTrue(tokens.matchesOperator("Bearer 아무 값"));
    }

    @Test
    @DisplayName("장치 토큰은 헤더 값이 정확히 같을 때만 통과한다")
    void deviceTokenMustMatchExactly() {
        ApiTokens tokens = new ApiTokens(DEVICE, OPERATOR);

        assertTrue(tokens.deviceRequired());
        assertTrue(tokens.matchesDevice(DEVICE));
        assertFalse(tokens.matchesDevice(null));
        assertFalse(tokens.matchesDevice(""));
        assertFalse(tokens.matchesDevice(DEVICE + "x"));
        assertFalse(tokens.matchesDevice(DEVICE.substring(1)));
        // 관제 토큰으로 장치 API를 부를 수는 없다.
        assertFalse(tokens.matchesDevice(OPERATOR));
    }

    @Test
    @DisplayName("관제 토큰은 Bearer 형식으로 보내야 통과한다")
    void operatorTokenNeedsBearerPrefix() {
        ApiTokens tokens = new ApiTokens(DEVICE, OPERATOR);

        assertTrue(tokens.operatorRequired());
        assertTrue(tokens.matchesOperator("Bearer " + OPERATOR));
        assertFalse(tokens.matchesOperator(null));
        assertFalse(tokens.matchesOperator(OPERATOR));
        assertFalse(tokens.matchesOperator("Bearer "));
        assertFalse(tokens.matchesOperator("Bearer " + OPERATOR + "x"));
        assertFalse(tokens.matchesOperator("Bearer " + DEVICE));
    }

    @Test
    @DisplayName("한쪽 토큰만 설정하면 그쪽만 검사한다")
    void tokensAreIndependent() {
        ApiTokens tokens = new ApiTokens("", OPERATOR);

        assertTrue(tokens.matchesDevice(null));
        assertFalse(tokens.matchesOperator(null));
    }

    @Test
    @DisplayName("쉽게 맞힐 수 있는 짧은 토큰으로는 서버가 시작되지 않는다")
    void rejectsShortToken() {
        assertThrows(IllegalStateException.class, () -> new ApiTokens("1234", ""));
        assertThrows(IllegalStateException.class, () -> new ApiTokens("", "short-token"));
    }
}
