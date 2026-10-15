package com.example.savedog.Report.service;

import com.example.savedog.Report.DTO.ReportResponse;
import com.example.savedog.Report.DTO.ReportSubmitRequest;
import com.example.savedog.Report.address.AddressResolver;
import com.example.savedog.Report.domain.HazardReport;
import com.example.savedog.Report.domain.ReportStatus;
import com.example.savedog.Report.reporter.HazardReporter;
import com.example.savedog.Report.repository.HazardReportRepository;
import com.example.savedog.Safety.analyzer.SafetyAssessment;
import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.Telemetry.service.CameraFrameService;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.storage.ImageStorage;
import com.example.savedog.global.validation.RequestValidator;
import com.example.savedog.global.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {
    // 이 반경 안에 최근 신고가 있으면 같은 위험으로 보고 새로 만들지 않는다.
    private static final double DUPLICATE_RADIUS_METERS = 30;
    private static final Duration DUPLICATE_WINDOW = Duration.ofHours(24);
    private static final Duration PHOTO_URL_VALID_FOR = Duration.ofMinutes(5);
    private static final int MAX_LISTED_REPORTS = 1_000;
    private static final int MAX_RECEIPT_NUMBER_LENGTH = 100;

    private final HazardReportRepository reportRepository;
    private final HazardReporter hazardReporter;
    private final AddressResolver addressResolver;
    private final CameraFrameService cameraFrameService;
    private final ImageStorage imageStorage;
    private final WebSocketService webSocketService;
    private final Clock clock;

    // 위험이 계속 감지됐을 때 불린다. 근처에 최근 신고가 있으면 만들지 않고 Optional.empty()
    public Optional<ReportResponse> createIfNew(Telemetry telemetry, SafetyAssessment assessment, SafetyRecord record) {
        GeoPoint location = new GeoPoint(record.getLatitude(), record.getLongitude());
        if (hasRecentReportNear(location)) {
            return Optional.empty();
        }
        HazardType hazardType = assessment.hazardType() == null ? HazardType.ETC : assessment.hazardType();
        String address = resolveAddress(location);

        HazardReport report = reportRepository.save(new HazardReport(
                record.getId(),
                hazardType,
                location.latitude(),
                location.longitude(),
                address,
                describe(hazardType, telemetry, assessment),
                assessment.level(),
                assessment.score(),
                assessment.reason()
        ));
        attachRecentPhoto(report);

        ReportResponse response = ReportResponse.from(report);
        webSocketService.broadcastReport(response);
        log.info("위험 신고 {} 생성: {} ({}, {})", report.getId(), hazardType, location.latitude(), location.longitude());
        return Optional.of(response);
    }

    @Transactional(readOnly = true)
    public List<ReportResponse> findAll(ReportStatus status) {
        Limit limit = Limit.of(MAX_LISTED_REPORTS);
        List<HazardReport> reports = status == null
                ? reportRepository.findAllByOrderByCreatedAtDesc(limit)
                : reportRepository.findByStatusOrderByCreatedAtDesc(status, limit);
        return reports.stream().map(ReportResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ReportResponse find(Long reportId) {
        return ReportResponse.from(load(reportId));
    }

    @Transactional(readOnly = true)
    public URI getPhotoUrl(Long reportId) {
        HazardReport report = load(reportId);
        if (!report.hasPhoto()) {
            throw new BusinessException(ErrorCode.FRAME_NOT_FOUND, "이 신고에는 첨부된 사진이 없습니다.");
        }
        return imageStorage.presignedUrl(report.getPhotoKey(), PHOTO_URL_VALID_FOR);
    }

    // 창구 전송에 실패하면 상태를 FAILED로 남긴 채 오류로 응답해야 하므로, 그 예외로는 롤백하지 않는다.
    @Transactional(noRollbackFor = BusinessException.class)
    public ReportResponse submit(Long reportId, ReportSubmitRequest request) {
        HazardReport report = load(reportId);
        report.ensureOpen();

        String receiptNumber = receiptNumberOf(request);
        if (receiptNumber == null) {
            receiptNumber = sendToAuthority(report);
        }
        report.markSubmitted(receiptNumber);
        return announceAfterCommit(report);
    }

    @Transactional
    public ReportResponse dismiss(Long reportId) {
        HazardReport report = load(reportId);
        report.dismiss();
        return announceAfterCommit(report);
    }

    private HazardReport load(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND));
    }

    private boolean hasRecentReportNear(GeoPoint location) {
        LocalDateTime since = LocalDateTime.now(clock).minus(DUPLICATE_WINDOW);
        return reportRepository.findByCreatedAtAfter(since).stream()
                .map(report -> new GeoPoint(report.getLatitude(), report.getLongitude()))
                .anyMatch(reported -> reported.distanceTo(location) <= DUPLICATE_RADIUS_METERS);
    }

    // 주소를 못 구해도(키 없음, 카카오 장애 등) 신고는 주소 없이 만든다.
    private String resolveAddress(GeoPoint location) {
        try {
            return addressResolver.resolve(location.latitude(), location.longitude()).orElse(null);
        } catch (RuntimeException e) {
            log.warn("주소를 구하지 못해 주소 없이 신고를 만듭니다: {}", e.toString());
            return null;
        }
    }

    private String describe(HazardType hazardType, Telemetry telemetry, SafetyAssessment assessment) {
        // 센서 값은 장치가 조용하면 비어 있을 수 있다. 근거가 된 값이 없으면 숫자 없이 적는다.
        return switch (hazardType) {
            case OBSTACLE -> telemetry.getDistance() == null
                    ? "보행로에 장애물이 있습니다."
                    : "보행로에 장애물이 있습니다. 로보독 전방 %dcm에서 감지."
                            .formatted(Math.round(telemetry.getDistance()));
            case ROUGH_ROAD -> telemetry.getRoll() == null || telemetry.getPitch() == null
                    ? "노면이 크게 기울어 있어 위험합니다."
                    : "노면이 크게 기울어 있어 위험합니다. 로보독 기울기 %d°로 감지."
                            .formatted(Math.round(Math.max(Math.abs(telemetry.getRoll()), Math.abs(telemetry.getPitch()))));
            case ETC -> assessment.reason() == null
                    ? "위험 구간이 감지되었습니다."
                    : "위험 구간이 감지되었습니다. (" + assessment.reason() + ")";
        };
    }

    // 방금 찍힌 프레임이 있으면 신고용 키로 복사해 둔다. 사진을 못 붙여도 신고는 그대로 둔다.
    private void attachRecentPhoto(HazardReport report) {
        try {
            Optional<String> frameKey = cameraFrameService.findRecentKey();
            if (frameKey.isEmpty()) {
                return;
            }
            String photoKey = "reports/" + report.getId() + ".jpg";
            imageStorage.copy(frameKey.get(), photoKey);
            report.attachPhoto(photoKey);
            reportRepository.save(report);
        } catch (RuntimeException e) {
            log.warn("신고 {}에 사진을 붙이지 못했습니다: {}", report.getId(), e.getMessage());
        }
    }

    private String receiptNumberOf(ReportSubmitRequest request) {
        if (request == null || request.receiptNumber() == null) {
            return null;
        }
        String receiptNumber = request.receiptNumber().trim();
        if (receiptNumber.isEmpty()) {
            throw RequestValidator.invalid("receiptNumber는 비워 둘 수 없습니다. 자동 제출하려면 항목을 빼고 보내 주세요.");
        }
        if (receiptNumber.length() > MAX_RECEIPT_NUMBER_LENGTH) {
            throw RequestValidator.invalid("receiptNumber는 %d자 이하여야 합니다.".formatted(MAX_RECEIPT_NUMBER_LENGTH));
        }
        // 줄바꿈 같은 제어 문자가 섞이면 화면·로그·내보낸 파일에서 다른 값처럼 보이게 꾸밀 수 있다.
        if (receiptNumber.chars().anyMatch(Character::isISOControl)) {
            throw RequestValidator.invalid("receiptNumber에 줄바꿈 같은 제어 문자를 넣을 수 없습니다.");
        }
        return receiptNumber;
    }

    private String sendToAuthority(HazardReport report) {
        try {
            String receiptNumber = hazardReporter.submit(report);
            if (receiptNumber == null || receiptNumber.isBlank()) {
                throw new BusinessException(ErrorCode.REPORT_SUBMIT_FAILED, "민원 창구가 접수번호를 주지 않았습니다.");
            }
            return receiptNumber;
        } catch (RuntimeException e) {
            report.markFailed();
            announceAfterCommit(report);
            if (e instanceof BusinessException failure && failure.getErrorCode() == ErrorCode.REPORT_SUBMIT_FAILED) {
                throw failure;
            }
            log.error("신고 {} 제출 중 예상하지 못한 오류", report.getId(), e);
            throw new BusinessException(ErrorCode.REPORT_SUBMIT_FAILED, ErrorCode.REPORT_SUBMIT_FAILED.getMessage(), e);
        }
    }

    // 상태 변경이 실제로 저장(커밋)된 뒤에만 화면에 알린다.
    private ReportResponse announceAfterCommit(HazardReport report) {
        ReportResponse response = ReportResponse.from(report);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    webSocketService.broadcastReport(response);
                }
            });
        } else {
            webSocketService.broadcastReport(response);
        }
        return response;
    }
}
