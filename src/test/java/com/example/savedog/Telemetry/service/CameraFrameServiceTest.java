package com.example.savedog.Telemetry.service;

import com.example.savedog.Vision.service.VisionService;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.storage.ImageStorage;
import com.example.savedog.global.storage.UnconfiguredImageStorage;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CameraFrameServiceTest {
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    private final MutableClock clock = new MutableClock();
    private final FakeStorage storage = new FakeStorage();
    private final VisionService vision = mock(VisionService.class);
    private final CameraFrameService service = new CameraFrameService(storage, vision, clock);

    @Test
    @DisplayName("프레임을 받으면 같은 키에 덮어써 저장한다")
    void storesLatestFrameUnderOneKey() {
        service.store(JPEG);
        service.store(JPEG);

        assertEquals(1, storage.objects.size());
        assertArrayEquals(JPEG, storage.objects.get(CameraFrameService.LATEST_FRAME_KEY));
    }

    @Test
    @DisplayName("최신 프레임 조회는 저장소가 준 주소를 돌려준다")
    void latestUrlComesFromStorage() {
        service.store(JPEG);

        assertEquals(URI.create("https://storage.test/frames/latest.jpg"), service.getLatestUrl());
    }

    @Test
    @DisplayName("받은 프레임이 없으면 FRAME_NOT_FOUND")
    void noFrameYet() {
        assertError(ErrorCode.FRAME_NOT_FOUND, service::getLatestUrl);
    }

    @Test
    @DisplayName("서버를 다시 켠 뒤에도 저장소에 남은 프레임은 조회된다")
    void frameLeftInStorageIsStillServed() {
        storage.objects.put(CameraFrameService.LATEST_FRAME_KEY, JPEG);

        assertEquals(URI.create("https://storage.test/frames/latest.jpg"), service.getLatestUrl());
    }

    @Test
    @DisplayName("신고 사진으로는 받은 지 10초 이내인 프레임만 쓴다")
    void onlyRecentFrameIsUsedForReports() {
        assertTrue(service.findRecentKey().isEmpty());

        service.store(JPEG);
        clock.advanceSeconds(10);
        assertEquals(CameraFrameService.LATEST_FRAME_KEY, service.findRecentKey().orElseThrow());

        clock.advanceSeconds(1);
        assertTrue(service.findRecentKey().isEmpty());
    }

    @Test
    @DisplayName("빈 본문, JPEG가 아닌 본문, 너무 큰 본문은 저장하지 않는다")
    void rejectsBadFrames() {
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(null));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(new byte[0]));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store("not a jpeg".getBytes()));

        byte[] tooLarge = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, tooLarge, 0, JPEG.length);
        assertError(ErrorCode.PAYLOAD_TOO_LARGE, () -> service.store(tooLarge));

        assertTrue(storage.objects.isEmpty());
    }

    @Test
    @DisplayName("S3를 설정하지 않았으면 이미지 기능만 503으로 응답한다")
    void unconfiguredStorage() {
        CameraFrameService unconfigured = new CameraFrameService(new UnconfiguredImageStorage(), vision, clock);

        assertError(ErrorCode.IMAGE_STORAGE_UNAVAILABLE, () -> unconfigured.store(JPEG));
        assertError(ErrorCode.IMAGE_STORAGE_UNAVAILABLE, unconfigured::getLatestUrl);
        assertTrue(unconfigured.findRecentKey().isEmpty());
    }

    @Test
    @DisplayName("저장에 실패하면 받은 것으로 치지 않는다")
    void failedUploadIsNotRemembered() {
        storage.failing = true;

        assertError(ErrorCode.IMAGE_STORAGE_UNAVAILABLE, () -> service.store(JPEG));
        assertTrue(service.findRecentKey().isEmpty());
    }

    @Test
    @DisplayName("물체 인식에 쓰이면 S3가 없어도 프레임을 받은 것으로 본다 (신고 사진으로는 쓰지 않는다)")
    void analyzedFrameIsAcceptedWithoutStorage() {
        when(vision.submit(JPEG)).thenReturn(true);
        CameraFrameService unconfigured = new CameraFrameService(new UnconfiguredImageStorage(), vision, clock);

        unconfigured.store(JPEG);
        unconfigured.store(JPEG);

        verify(vision, times(2)).submit(JPEG);
        assertTrue(unconfigured.findRecentKey().isEmpty());
    }

    @Test
    @DisplayName("S3에 못 넣는 동안에는 서명한 임시 주소로 메모리의 최신 사진을 준다")
    void liveFrameWhenStorageFails() {
        when(vision.submit(JPEG)).thenReturn(true);
        storage.failing = true;
        service.store(JPEG);

        URI url = service.getLatestUrl();

        assertTrue(url.toString().startsWith("https://server.test/api/robot/frames/live?expires="), url.toString());
        assertArrayEquals(JPEG, service.getLiveFrame(expiresOf(url), signatureOf(url)));
    }

    @Test
    @DisplayName("서명이 틀리거나 다른 만료 시각이거나 주소가 만료되면 401")
    void liveFrameNeedsValidSignature() {
        when(vision.submit(JPEG)).thenReturn(true);
        storage.failing = true;
        service.store(JPEG);
        URI url = service.getLatestUrl();
        long expires = expiresOf(url);
        String signature = signatureOf(url);

        assertError(ErrorCode.UNAUTHORIZED, () -> service.getLiveFrame(expires, "wrong"));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.getLiveFrame(expires + 1, signature));

        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertError(ErrorCode.UNAUTHORIZED, () -> service.getLiveFrame(expires, signature));
    }

    @Test
    @DisplayName("사진이 10초 넘게 안 오면 옛 사진을 지금 모습처럼 보여 주지 않는다")
    void staleLiveFrameIsNotShown() {
        when(vision.submit(JPEG)).thenReturn(true);
        storage.failing = true;
        service.store(JPEG);
        URI url = service.getLatestUrl();

        clock.advanceSeconds(11);

        assertError(ErrorCode.FRAME_NOT_FOUND, service::getLatestUrl);
        assertError(ErrorCode.FRAME_NOT_FOUND, () -> service.getLiveFrame(expiresOf(url), signatureOf(url)));
    }

    @Test
    @DisplayName("S3가 다시 되면 S3 주소로 돌아가고 메모리의 사진은 버린다")
    void returnsToStorageWhenItRecovers() {
        when(vision.submit(JPEG)).thenReturn(true);
        storage.failing = true;
        service.store(JPEG);
        URI live = service.getLatestUrl();

        storage.failing = false;
        service.store(JPEG);

        assertTrue(service.getLatestUrl().toString().startsWith("https://storage.test/"));
        assertError(ErrorCode.FRAME_NOT_FOUND, () -> service.getLiveFrame(expiresOf(live), signatureOf(live)));
    }

    private static long expiresOf(URI url) {
        return Long.parseLong(url.getQuery().split("&")[0].substring("expires=".length()));
    }

    private static String signatureOf(URI url) {
        return url.getQuery().split("&")[1].substring("signature=".length());
    }

    @Test
    @DisplayName("JPEG가 아니거나 비어 있으면 물체 인식에도 넘기지 않는다")
    void invalidFrameIsNotAnalyzed() {
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(new byte[]{1, 2, 3}));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.store(new byte[0]));

        verify(vision, never()).submit(any());
    }

    private static class FakeStorage implements ImageStorage {
        private final Map<String, byte[]> objects = new HashMap<>();
        private boolean failing;

        @Override
        public void put(String key, byte[] content, String contentType) {
            if (failing) {
                throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE);
            }
            objects.put(key, content);
        }

        @Override
        public void copy(String sourceKey, String targetKey) {
            objects.put(targetKey, objects.get(sourceKey));
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        @Override
        public URI presignedUrl(String key, Duration validFor) {
            return URI.create("https://storage.test/" + key);
        }
    }
}
