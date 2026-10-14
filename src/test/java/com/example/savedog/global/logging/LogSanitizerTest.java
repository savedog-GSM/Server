package com.example.savedog.global.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSanitizerTest {

    @Test
    @DisplayName("줄바꿈을 지워 가짜 로그 줄을 끼워 넣지 못하게 한다")
    void removesLineBreaks() {
        String cleaned = LogSanitizer.clean("OK\r\n2026-10-13 ERROR 관리자 비밀번호가 변경되었습니다");

        assertFalse(cleaned.contains("\n"));
        assertFalse(cleaned.contains("\r"));
        assertEquals("OK  2026-10-13 ERROR 관리자 비밀번호가 변경되었습니다", cleaned);
    }

    @Test
    @DisplayName("너무 긴 값은 잘라서 남긴다")
    void truncatesLongValues() {
        String cleaned = LogSanitizer.clean("x".repeat(100_000));

        assertTrue(cleaned.length() < 250, "길이 " + cleaned.length());
        assertTrue(cleaned.endsWith("...(100000자)"));
    }

    @Test
    @DisplayName("평범한 값과 null은 그대로 다룬다")
    void keepsOrdinaryValues() {
        assertEquals("{\"commandId\":1,\"result\":\"OK\"}", LogSanitizer.clean("{\"commandId\":1,\"result\":\"OK\"}"));
        assertEquals("null", LogSanitizer.clean(null));
    }
}
