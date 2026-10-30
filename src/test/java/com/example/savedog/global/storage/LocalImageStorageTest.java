package com.example.savedog.global.storage;

import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalImageStorageTest {
    private static final byte[] IMAGE = {1, 2, 3};
    private static final Duration FIVE_MINUTES = Duration.ofMinutes(5);

    @TempDir
    Path root;

    private final MutableClock clock = new MutableClock();

    private LocalImageStorage storage() {
        return new LocalImageStorage(root.resolve("images"), "https://server.test/", clock);
    }

    @Test
    @DisplayName("저장·덮어쓰기·복사·존재 확인이 되고, 임시 파일이 남지 않는다")
    void putCopyExists() throws Exception {
        LocalImageStorage storage = storage();
        assertFalse(storage.exists("frames/latest.jpg"));

        storage.put("frames/latest.jpg", new byte[]{9}, "image/jpeg");
        storage.put("frames/latest.jpg", IMAGE, "image/jpeg");
        storage.copy("frames/latest.jpg", "reports/7.jpg");

        assertTrue(storage.exists("reports/7.jpg"));
        assertArrayEquals(IMAGE, Files.readAllBytes(root.resolve("images/reports/7.jpg")));
        try (var files = Files.list(root.resolve("images/frames"))) {
            assertEquals(1, files.count());
        }
    }

    @Test
    @DisplayName("없는 파일을 복사하면 저장소 오류로 알린다 (신고는 사진 없이 만들어진다)")
    void copyMissingFile() {
        assertError(ErrorCode.IMAGE_STORAGE_UNAVAILABLE, () -> storage().copy("frames/latest.jpg", "reports/1.jpg"));
    }

    @Test
    @DisplayName("서명한 주소로 읽고, 서명·키·만료 시각이 다르거나 만료되면 401")
    void signedRead() {
        LocalImageStorage storage = storage();
        storage.put("reports/7.jpg", IMAGE, "image/jpeg");
        storage.put("reports/8.jpg", IMAGE, "image/jpeg");
        URI url = storage.presignedUrl("reports/7.jpg", FIVE_MINUTES);
        assertTrue(url.toString().startsWith("https://server.test/api/images?key=reports%2F7.jpg&expires="), url.toString());

        Map<String, String> query = queryOf(url);
        long expires = Long.parseLong(query.get("expires"));
        String signature = query.get("signature");

        assertArrayEquals(IMAGE, storage.read("reports/7.jpg", expires, signature));
        assertError(ErrorCode.UNAUTHORIZED, () -> storage.read("reports/7.jpg", expires, "wrong"));
        assertError(ErrorCode.UNAUTHORIZED, () -> storage.read("reports/8.jpg", expires, signature));
        assertError(ErrorCode.UNAUTHORIZED, () -> storage.read("reports/7.jpg", expires + 1, signature));

        clock.advance(FIVE_MINUTES.plusSeconds(1));
        assertError(ErrorCode.UNAUTHORIZED, () -> storage.read("reports/7.jpg", expires, signature));
    }

    @Test
    @DisplayName("파일이 사라졌으면 404")
    void deletedFile() throws Exception {
        LocalImageStorage storage = storage();
        storage.put("reports/7.jpg", IMAGE, "image/jpeg");
        Map<String, String> query = queryOf(storage.presignedUrl("reports/7.jpg", FIVE_MINUTES));
        Files.delete(root.resolve("images/reports/7.jpg"));

        assertError(ErrorCode.FRAME_NOT_FOUND,
                () -> storage.read("reports/7.jpg", Long.parseLong(query.get("expires")), query.get("signature")));
    }

    @Test
    @DisplayName("보관 폴더 밖을 가리키는 경로는 막는다")
    void rejectsPathsOutsideRoot() throws Exception {
        LocalImageStorage storage = storage();
        Files.writeString(root.resolve("secret.env"), "SECRET=1");

        assertError(ErrorCode.INVALID_REQUEST, () -> storage.exists("../secret.env"));
        assertError(ErrorCode.INVALID_REQUEST, () -> storage.put("../evil.jpg", IMAGE, "image/jpeg"));
        assertError(ErrorCode.INVALID_REQUEST, () -> storage.presignedUrl("../secret.env", FIVE_MINUTES));
        assertError(ErrorCode.INVALID_REQUEST, () -> storage.exists(root.resolve("secret.env").toString()));
        assertFalse(Files.exists(root.resolve("evil.jpg")));
    }

    private static Map<String, String> queryOf(URI url) {
        return Arrays.stream(url.getRawQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
