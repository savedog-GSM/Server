package com.example.savedog.global.logging;

// 바깥에서 들어온 문자열을 로그에 남기기 전에 다듬는다.
public final class LogSanitizer {
    private static final int MAX_LENGTH = 200;

    private LogSanitizer() {
    }

    /**
     * 줄바꿈 같은 제어 문자를 지워 가짜 로그 줄을 끼워 넣지 못하게 하고,
     * 너무 긴 값은 잘라 로그가 한없이 불어나지 않게 한다.
     */
    public static String clean(String value) {
        if (value == null) {
            return "null";
        }
        String cleaned = value.replaceAll("\\p{Cntrl}", " ");
        return cleaned.length() <= MAX_LENGTH
                ? cleaned
                : cleaned.substring(0, MAX_LENGTH) + "...(" + cleaned.length() + "자)";
    }
}
