package com.example.savedog.Telemetry.service;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.security.ApiRequestGuardFilter;
import com.example.savedog.global.storage.ImageStorage;
import com.example.savedog.global.storage.JpegImage;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

// 프레임 이미지는 S3에 둔다. 최신 1장을 같은 키에 덮어써서 용량이 늘지 않게 한다.
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
    private final Clock clock;
    private final AtomicReference<LocalDateTime> lastStoredAt = new AtomicReference<>();

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
        imageStorage.put(LATEST_FRAME_KEY, jpeg, JPEG_CONTENT_TYPE);
        lastStoredAt.set(LocalDateTime.now(clock));
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
