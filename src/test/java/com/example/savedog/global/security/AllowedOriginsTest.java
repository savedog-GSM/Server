package com.example.savedog.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class AllowedOriginsTest {

    @Test
    @DisplayName("쉼표로 나눈 출처를 앞뒤 공백 없이 읽는다")
    void parsesCommaSeparatedOrigins() {
        AllowedOrigins origins = new AllowedOrigins(" http://localhost:5173 , http://192.168.0.10:3000,");

        assertArrayEquals(new String[]{"http://localhost:5173", "http://192.168.0.10:3000"}, origins.values());
    }

    @Test
    @DisplayName("값 없이 적어 두면 모든 출처가 막히지 않도록 기본 출처를 쓴다")
    void blankFallsBackToDefaults() {
        String[] defaults = {"http://localhost:3000", "http://localhost:5173"};

        assertArrayEquals(defaults, new AllowedOrigins("").values());
        assertArrayEquals(defaults, new AllowedOrigins("   ").values());
        assertArrayEquals(defaults, new AllowedOrigins(null).values());
    }
}
