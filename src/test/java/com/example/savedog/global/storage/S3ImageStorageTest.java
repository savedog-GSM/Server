package com.example.savedog.global.storage;

import com.example.savedog.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 실제 AWS 대신 정해진 상태 코드만 돌려주는 가짜 S3 서버로 확인한다.
class S3ImageStorageTest {
    private static final String BUCKET = "test-bucket";

    // 경로별로 돌려줄 상태 코드. 없으면 404
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private HttpServer server;
    private S3ImageStorage storage;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int status = statuses.getOrDefault(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath(), 404);
            exchange.getResponseHeaders().set("ETag", "\"etag\"");
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        storage = new S3ImageStorage(BUCKET, "ap-northeast-2", "test-access-key", "test-secret-key", "",
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void stop() {
        storage.close();
        server.stop(0);
    }

    @Test
    @DisplayName("파일 확인: 있으면 true, 404면 false, 목록 권한이 없어 403이 와도 오류 대신 false")
    void existsTreatsForbiddenHeadAsMissing() {
        statuses.put("HEAD /test-bucket/frames/latest.jpg", 200);
        assertTrue(storage.exists("frames/latest.jpg"));

        statuses.remove("HEAD /test-bucket/frames/latest.jpg");
        assertFalse(storage.exists("frames/latest.jpg"));

        statuses.put("HEAD /test-bucket/frames/latest.jpg", 403);
        assertFalse(storage.exists("frames/latest.jpg"));
        assertFalse(storage.exists("frames/latest.jpg"));
    }

    @Test
    @DisplayName("올리기가 403이면(쓰기 권한 없음) 지금처럼 이미지 저장소 오류(503)로 알린다")
    void forbiddenPutIsStillAnError() {
        statuses.put("PUT /test-bucket/frames/latest.jpg", 403);

        assertError(ErrorCode.IMAGE_STORAGE_UNAVAILABLE,
                () -> storage.put("frames/latest.jpg", new byte[]{1, 2, 3}, "image/jpeg"));
    }

    @Test
    @DisplayName("파일 확인 중 서버 오류(500)는 숨기지 않고 이미지 저장소 오류로 알린다")
    void serverErrorOnHeadIsStillAnError() {
        statuses.put("HEAD /test-bucket/frames/latest.jpg", 500);

        assertError(ErrorCode.IMAGE_STORAGE_UNAVAILABLE, () -> storage.exists("frames/latest.jpg"));
    }
}
