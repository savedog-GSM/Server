package com.example.savedog.Report.service;

import com.example.savedog.Report.DTO.HazardUploadRequest;
import com.example.savedog.Report.DTO.HazardUploadResult;
import com.example.savedog.Report.DTO.ReportResponse;
import com.example.savedog.Report.address.AddressResolver;
import com.example.savedog.Report.domain.HazardReport;
import com.example.savedog.Report.repository.HazardReportRepository;
import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Telemetry.domain.RobotLocation;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.security.ApiRequestGuardFilter;
import com.example.savedog.global.storage.ImageStorage;
import com.example.savedog.global.storage.JpegImage;
import com.example.savedog.global.validation.RequestValidator;
import com.example.savedog.global.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

/**
 * 중계 프로그램이 위험 QR(warning, fire, ground)을 보고 보낸 사진과 정보로 신고를 만든다.
 * 위치는 스마트폰이 보낸 최근 위치를 붙이고, 근처에 같은 종류의 최근 신고가 있으면 새로 만들지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HazardUploadService {
    // 로보독이 멈춰 선 뒤 사진을 찍어 올리기까지 시간이 걸리므로 센서 값(5초)보다 넉넉히 본다.
    private static final Duration LOCATION_MAX_AGE = Duration.ofSeconds(60);
    // 자동 생성 신고와 같은 기준. 이 반경·기간 안에 같은 종류의 신고가 있으면 같은 위험으로 본다.
    private static final double DUPLICATE_RADIUS_METERS = 30;
    private static final Duration DUPLICATE_WINDOW = Duration.ofHours(24);
    private static final int MAX_IMAGE_BYTES = ApiRequestGuardFilter.MAX_FRAME_BYTES;
    private static final int MAX_LABEL_LENGTH = 64;
    private static final int DANGER_SCORE = 5;
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final String ALLOWED_TYPES = "GENERAL_DANGER, FIRE, GROUND_DAMAGE";

    private final HazardReportRepository reportRepository;
    private final LocationService locationService;
    private final AddressResolver addressResolver;
    private final ImageStorage imageStorage;
    private final WebSocketService webSocketService;
    private final Clock clock;

    // 같은 위험을 두 요청이 동시에 올려도 신고가 두 건 생기지 않게, 중복 확인과 저장을 한 번에 하나씩 한다. (서버 한 대 기준)
    private final Object creating = new Object();

    public HazardUploadResult upload(HazardUploadRequest request) {
        RequestValidator.required(request, "요청 본문");
        HazardType hazardType = hazardTypeOf(request.hazardType());
        label(request.robotId(), "robotId");
        label(request.source(), "source");
        String qrCode = label(request.qrCode(), "qrCode");
        byte[] image = checkedImage(request.image());

        RobotLocation location = locationService.findRecent(LOCATION_MAX_AGE).orElseThrow(() -> new BusinessException(
                ErrorCode.LOCATION_UNAVAILABLE,
                "최근 %d초 안에 받은 스마트폰 위치가 없어 신고를 만들 수 없습니다. 스마트폰의 위치 전송이 켜져 있는지 확인하세요."
                        .formatted(LOCATION_MAX_AGE.toSeconds())
        ));
        GeoPoint point = new GeoPoint(location.latitude(), location.longitude());
        // 카카오 호출은 느릴 수 있으므로 아래의 한 번에 하나씩 하는 구간 밖에서 미리 구한다.
        String address = resolveAddress(point);

        HazardReport report;
        synchronized (creating) {
            Optional<HazardReport> duplicate = findDuplicate(hazardType, point);
            if (duplicate.isPresent()) {
                log.info("같은 위험의 최근 신고 {}가 있어 새로 만들지 않았습니다: {} ({}, {})",
                        duplicate.get().getId(), hazardType, point.latitude(), point.longitude());
                return new HazardUploadResult(ReportResponse.from(duplicate.get()), false);
            }
            report = reportRepository.save(new HazardReport(
                    null,
                    hazardType,
                    point.latitude(),
                    point.longitude(),
                    address,
                    describe(hazardType, qrCode),
                    SafetyLevel.DANGER,
                    DANGER_SCORE,
                    reason(hazardType, qrCode)
            ));
        }
        if (image != null) {
            report = attachPhoto(report, image);
        }

        ReportResponse response = ReportResponse.from(report);
        webSocketService.broadcastReport(response);
        log.info("중계 프로그램의 위험 신고 {} 생성: {} ({}, {}) 사진 {}",
                report.getId(), hazardType, point.latitude(), point.longitude(), report.hasPhoto() ? "있음" : "없음");
        return new HazardUploadResult(response, true);
    }

    private HazardType hazardTypeOf(String value) {
        if (value == null || value.isBlank()) {
            throw RequestValidator.invalid("hazardType 값이 필요합니다. (" + ALLOWED_TYPES + ")");
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "FIRE" -> HazardType.FIRE;
            case "GROUND_DAMAGE" -> HazardType.ROUGH_ROAD;
            case "GENERAL_DANGER" -> HazardType.ETC;
            default -> throw RequestValidator.invalid("hazardType은 " + ALLOWED_TYPES + " 중 하나여야 합니다.");
        };
    }

    // 기록용 짧은 글자. 없으면 null
    private String label(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LABEL_LENGTH) {
            throw RequestValidator.invalid("%s는 %d자 이하여야 합니다.".formatted(name, MAX_LABEL_LENGTH));
        }
        // 줄바꿈 같은 제어 문자가 섞이면 신고 설명·로그에서 다른 내용처럼 꾸밀 수 있다.
        if (trimmed.chars().anyMatch(Character::isISOControl)) {
            throw RequestValidator.invalid(name + "에 줄바꿈 같은 제어 문자를 넣을 수 없습니다.");
        }
        return trimmed;
    }

    // 사진은 없어도 신고를 만든다. 보냈다면 2MB 이하의 JPEG여야 한다.
    private byte[] checkedImage(byte[] image) {
        if (image == null) {
            return null;
        }
        if (image.length == 0) {
            throw RequestValidator.invalid("image가 비어 있습니다.");
        }
        if (image.length > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "image는 2MB 이하여야 합니다.");
        }
        if (!JpegImage.looksLikeJpeg(image)) {
            throw RequestValidator.invalid("image는 JPEG 이미지여야 합니다.");
        }
        return image;
    }

    private Optional<HazardReport> findDuplicate(HazardType hazardType, GeoPoint point) {
        LocalDateTime since = LocalDateTime.now(clock).minus(DUPLICATE_WINDOW);
        return reportRepository.findByCreatedAtAfter(since).stream()
                .filter(report -> report.getHazardType() == hazardType)
                .filter(report -> new GeoPoint(report.getLatitude(), report.getLongitude())
                        .distanceTo(point) <= DUPLICATE_RADIUS_METERS)
                .max(Comparator.comparing(HazardReport::getCreatedAt));
    }

    // 사진을 못 올려도(S3 미설정·장애) 신고는 사진 없이 남긴다.
    private HazardReport attachPhoto(HazardReport report, byte[] image) {
        String photoKey = "reports/" + report.getId() + ".jpg";
        try {
            imageStorage.put(photoKey, image, JPEG_CONTENT_TYPE);
            report.attachPhoto(photoKey);
            return reportRepository.save(report);
        } catch (RuntimeException e) {
            log.warn("신고 {}의 사진을 저장하지 못해 사진 없이 둡니다: {}", report.getId(), e.getMessage());
            return report;
        }
    }

    // 주소를 못 구해도(키 없음, 카카오 장애 등) 신고는 주소 없이 만든다.
    private String resolveAddress(GeoPoint point) {
        try {
            return addressResolver.resolve(point.latitude(), point.longitude()).orElse(null);
        } catch (RuntimeException e) {
            log.warn("주소를 구하지 못해 주소 없이 신고를 만듭니다: {}", e.toString());
            return null;
        }
    }

    private String describe(HazardType hazardType, String qrCode) {
        String what = switch (hazardType) {
            case FIRE -> "화재가 감지되었습니다.";
            case ROUGH_ROAD -> "노면이 파손되어 있습니다.";
            default -> "위험 요소가 감지되었습니다.";
        };
        return qrCode == null
                ? what + " 로보독이 현장에서 신고했습니다."
                : what + " 로보독이 QR(" + qrCode + ")을 인식해 신고했습니다.";
    }

    private String reason(HazardType hazardType, String qrCode) {
        String what = switch (hazardType) {
            case FIRE -> "화재";
            case ROUGH_ROAD -> "노면 파손";
            default -> "위험 요소";
        };
        return qrCode == null ? what : what + " (QR " + qrCode + ")";
    }
}
