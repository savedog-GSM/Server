package com.example.savedog.Telemetry.service;

import com.example.savedog.Vision.service.VisionService;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.security.ApiRequestGuardFilter;
import com.example.savedog.global.storage.ImageStorage;
import com.example.savedog.global.storage.JpegImage;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

// 프레임 이미지는 S3에 둔다. 최신 1장을 같은 키에 덮어써서 용량이 늘지 않게 한다.
// 물체 인식 모델이 켜져 있으면 받은 프레임을 VisionService에도 넘긴다.
@Slf4j
@Service
@RequiredArgsConstructor
public class CameraFrameService {
    public static final String LATEST_FRAME_KEY = "frames/latest.jpg";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    // 요청을 받는 단계(ApiRequestGuardFilter)에서 같은 값으로 먼저 걸러진다.
    private static final int MAX_FRAME_BYTES = ApiRequestGuardFilter.MAX_FRAME_BYTES;
    private static final Duration URL_VALID_FOR = Duration.ofMinutes(5);
    private static final Duration RECENT = Duration.ofSeconds(10);

    private final ImageStorage imageStorage;
    private final VisionService visionService;
    private final Clock clock;
    private final AtomicReference<LocalDateTime> lastStoredAt = new AtomicReference<>();
    private final AtomicBoolean storageWarned = new AtomicBoolean();

    public void store(byte[] jpeg) {
        if (jpeg == null || jpeg.length == 0) {
            throw RequestValidator.invalid("이미지 본문이 비어 있습니다.");
        }
        if (jpeg.length > MAX_FRAME_BYTES) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "프레임은 2MB 이하여야 합니다.");
        }
        if (!JpegImage.looksLikeJpeg(jpeg)) {
            throw RequestValidator.invalid("JPEG 이미지가 아닙니다.");
        }
        boolean analyzing = visionService.submit(jpeg);
        try {
            imageStorage.put(LATEST_FRAME_KEY, jpeg, JPEG_CONTENT_TYPE);
        } catch (BusinessException e) {
            // 카메라 판단에 쓰고 있다면 S3가 없거나 잠깐 안 돼도 프레임은 받은 것으로 본다. (화면 표시·신고 사진만 빠진다)
            if (analyzing && e.getErrorCode() == ErrorCode.IMAGE_STORAGE_UNAVAILABLE) {
                warnStorageOnce(e);
                return;
            }
            throw e;
        }
        lastStoredAt.set(LocalDateTime.now(clock));
    }

    private void warnStorageOnce(BusinessException e) {
        if (storageWarned.compareAndSet(false, true)) {
            log.warn("카메라 프레임을 S3에 저장하지 못해 물체 인식에만 씁니다. (이후 같은 경고는 생략) {}", e.getMessage());
        }
    }

    public URI getLatestUrl() {
        // 서버를 다시 켠 뒤에는 받은 시각을 잊으므로 저장소에 남아 있는지 직접 확인한다.
        if (lastStoredAt.get() == null && !imageStorage.exists(LATEST_FRAME_KEY)) {
            throw new BusinessException(ErrorCode.FRAME_NOT_FOUND);
        }
        return imageStorage.presignedUrl(LATEST_FRAME_KEY, URL_VALID_FOR);
    }

    // 신고 사진용. 받은 지 10초 이내인 프레임이 있을 때만 그 키를 돌려준다.
    public Optional<String> findRecentKey() {
        LocalDateTime storedAt = lastStoredAt.get();
        if (storedAt == null) {
            return Optional.empty();
        }
        boolean recent = Duration.between(storedAt, LocalDateTime.now(clock)).compareTo(RECENT) <= 0;
        return recent ? Optional.of(LATEST_FRAME_KEY) : Optional.empty();
    }
}
