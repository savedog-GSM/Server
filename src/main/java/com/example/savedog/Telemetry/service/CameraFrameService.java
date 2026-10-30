package com.example.savedog.Telemetry.service;

import com.example.savedog.Vision.service.VisionService;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.security.ApiRequestGuardFilter;
import com.example.savedog.global.storage.ImageStorage;
import com.example.savedog.global.storage.JpegImage;
import com.example.savedog.global.validation.RequestValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

// 프레임 이미지는 S3에 둔다. 최신 1장을 같은 키에 덮어써서 용량이 늘지 않게 한다.
// 물체 인식 모델이 켜져 있으면 받은 프레임을 VisionService에도 넘긴다.
// S3에 저장하지 못한 동안에는 최신 1장을 메모리에 두고, 서명한 임시 주소로 화면에 보여 준다.
@Slf4j
@Service
public class CameraFrameService {
    public static final String LATEST_FRAME_KEY = "frames/latest.jpg";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    // 요청을 받는 단계(ApiRequestGuardFilter)에서 같은 값으로 먼저 걸러진다.
    private static final int MAX_FRAME_BYTES = ApiRequestGuardFilter.MAX_FRAME_BYTES;
    private static final Duration URL_VALID_FOR = Duration.ofMinutes(5);
    private static final Duration RECENT = Duration.ofSeconds(10);
    private static final String SIGNING_ALGORITHM = "HmacSHA256";

    private final ImageStorage imageStorage;
    private final VisionService visionService;
    private final Clock clock;
    private final String publicBaseUrl;
    private final byte[] signingKey = new byte[32];
    private final AtomicReference<LocalDateTime> lastStoredAt = new AtomicReference<>();
    private final AtomicReference<LiveFrame> liveFrame = new AtomicReference<>();
    private final AtomicBoolean storageWarned = new AtomicBoolean();

    @Autowired
    public CameraFrameService(
            ImageStorage imageStorage,
            VisionService visionService,
            Clock clock,
            @Value("${savedog.public-base-url:}") String publicBaseUrl
    ) {
        this.imageStorage = imageStorage;
        this.visionService = visionService;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl.trim().replaceAll("/+$", "");
        new SecureRandom().nextBytes(signingKey);
    }

    // 테스트용
    CameraFrameService(ImageStorage imageStorage, VisionService visionService, Clock clock) {
        this(imageStorage, visionService, clock, "https://server.test");
    }

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
            // 카메라 판단에 쓰고 있다면 S3가 없거나 잠깐 안 돼도 프레임은 받은 것으로 본다.
            // 화면에는 메모리의 최신 1장을 보여 준다. (신고 사진은 S3가 있어야 붙는다)
            if (analyzing && e.getErrorCode() == ErrorCode.IMAGE_STORAGE_UNAVAILABLE) {
                warnStorageOnce(e);
                liveFrame.set(new LiveFrame(jpeg, LocalDateTime.now(clock)));
                return;
            }
            throw e;
        }
        liveFrame.set(null);
        lastStoredAt.set(LocalDateTime.now(clock));
    }

    private void warnStorageOnce(BusinessException e) {
        if (storageWarned.compareAndSet(false, true)) {
            log.warn("카메라 프레임을 S3에 저장하지 못해 서버 메모리의 최신 1장만 씁니다. 신고 사진은 붙지 않습니다. (이후 같은 경고는 생략) {}",
                    e.getMessage());
        }
    }

    public URI getLatestUrl() {
        // S3에 못 넣고 있는 동안에는 메모리의 사진을 보여 준다. 오래된 사진을 지금 모습처럼 보이지 않게 최근 것만 쓴다.
        LiveFrame live = liveFrame.get();
        if (live != null) {
            if (!isRecent(live.receivedAt())) {
                throw new BusinessException(ErrorCode.FRAME_NOT_FOUND);
            }
            return liveFrameUrl();
        }
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
        return isRecent(storedAt) ? Optional.of(LATEST_FRAME_KEY) : Optional.empty();
    }

    /**
     * 서명한 임시 주소로 메모리의 최신 사진을 꺼낸다. 주소는 관제 토큰으로 조회한 사람만 받을 수 있다.
     * img 태그는 인증 헤더를 못 붙이므로 S3 presigned URL처럼 주소 자체에 서명을 넣는다.
     */
    public byte[] getLiveFrame(long expires, String signature) {
        boolean valid = MessageDigest.isEqual(
                sign(expires).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
        if (!valid || clock.millis() > expires) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        LiveFrame live = liveFrame.get();
        if (live == null || !isRecent(live.receivedAt())) {
            throw new BusinessException(ErrorCode.FRAME_NOT_FOUND);
        }
        return live.jpeg();
    }

    // 만료 시각이 매번 달라지므로 주소도 매번 달라져, 브라우저가 옛 사진을 캐시에서 꺼내 보여 주지 않는다.
    private URI liveFrameUrl() {
        if (publicBaseUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE,
                    "S3에 사진을 저장하지 못했고 PUBLIC_BASE_URL도 설정되지 않아 사진 주소를 만들 수 없습니다.");
        }
        long expires = clock.millis() + URL_VALID_FOR.toMillis();
        return URI.create("%s/api/robot/frames/live?expires=%d&signature=%s"
                .formatted(publicBaseUrl, expires, sign(expires)));
    }

    private String sign(long expires) {
        try {
            Mac mac = Mac.getInstance(SIGNING_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, SIGNING_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(Long.toString(expires).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("사진 주소 서명 실패", e);
        }
    }

    private boolean isRecent(LocalDateTime at) {
        return Duration.between(at, LocalDateTime.now(clock)).compareTo(RECENT) <= 0;
    }

    private record LiveFrame(byte[] jpeg, LocalDateTime receivedAt) {
    }
}
