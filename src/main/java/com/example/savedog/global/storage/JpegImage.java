package com.example.savedog.global.storage;

public final class JpegImage {
    private JpegImage() {
    }

    // JPEG 파일은 항상 FF D8 FF로 시작한다.
    public static boolean looksLikeJpeg(byte[] bytes) {
        return bytes != null
                && bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }
}
