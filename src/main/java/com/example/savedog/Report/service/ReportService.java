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
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    // 민원 창구가 이 시간 안에 답하지 않으면 제출 실패로 본다.
    private static final Duration SUBMIT_TIMEOUT = Duration.ofSeconds(10);
    // 창구 호출은 응답을 기다리는 동안 스레드를 붙잡으므로 가벼운 가상 스레드에서 돌린다.
    private static final Executor AUTHORITY_CALLS = task -> Thread.ofVirtual().name("hazard-reporter").start(task);

    private final HazardReportRepository reportRepository;
    private final HazardReporter hazardReporter;
    private final AddressResolver addressResolver;
    private final CameraFrameService cameraFrameService;
    private final ImageStorage imageStorage;
    private final WebSocketService webSocketService;
    private final TransactionOperations transactions;
    private final Clock clock;

    // 제출·기각을 처리하는 중인 신고. 창구에 보내는 동안에는 DB 트랜잭션을 열어 두지 않으므로,
    // 같은 신고를 두 요청이 동시에 창구로 보내지 않게 여기서 막는다. (서버 한 대 기준)
    private final Set<Long> inProgress = ConcurrentHashMap.newKeySet();
    private Duration submitTimeout = SUBMIT_TIMEOUT;

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

    /**
     * 창구 호출은 느릴 수 있어서 DB 트랜잭션 밖에서 한다. (이슈 #6)
     * 상태 확인과 결과 저장만 각각 짧은 트랜잭션으로 하고, 창구 전송에 실패하면 상태를 FAILED로 남긴 채 오류로 응답한다.
     */
    public ReportResponse submit(Long reportId, ReportSubmitRequest request) {
        String givenReceiptNumber = receiptNumberOf(request);
        claim(reportId);
        try {
            String receiptNumber = givenReceiptNumber;
            if (receiptNumber == null) {
                HazardReport report = transactions.execute(status -> openReport(reportId));
                receiptNumber = sendToAuthority(reportId, report);
            }
            String submitted = receiptNumber;
            return transactions.execute(status -> {
                HazardReport report = openReport(reportId);
                report.markSubmitted(submitted);
                return announceAfterCommit(report);
            });
        } finally {
            inProgress.remove(reportId);
        }
    }

    public ReportResponse dismiss(Long reportId) {
        claim(reportId);
        try {
            return transactions.execute(status -> {
                HazardReport report = load(reportId);
                report.dismiss();
                return announceAfterCommit(report);
            });
        } finally {
            inProgress.remove(reportId);
        }
    }

    // 테스트에서 창구 응답 시간 제한을 줄이기 위한 것
    void setSubmitTimeout(Duration submitTimeout) {
        this.submitTimeout = submitTimeout;
    }

    private void claim(Long reportId) {
        if (!inProgress.add(reportId)) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_PROCESSED, "이 신고는 지금 다른 요청이 처리하는 중입니다.");
        }
    }

    private HazardReport openReport(Long reportId) {
        HazardReport report = load(reportId);
        report.ensureOpen();
        return report;
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
        return switch (hazardType) {
            case OBSTACLE -> telemetry.getDistance() == null
                    ? "보행로에 장애물이 있습니다."
                    : "보행로에 장애물이 있습니다. 로보독 전방 %dcm에서 감지."
                            .formatted(Math.round(telemetry.getDistance()));
            // 기울기 판단을 없애서 새 신고에는 나오지 않는다.
            case ROUGH_ROAD -> "노면이 위험합니다.";
            case FIRE -> "화재가 감지되었습니다.";
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

    // 트랜잭션 밖에서 창구로 보낸다. 실패하면 짧은 트랜잭션으로 FAILED를 남기고 REPORT_SUBMIT_FAILED로 알린다.
    private String sendToAuthority(Long reportId, HazardReport report) {
        try {
            String receiptNumber = callAuthority(report);
            if (receiptNumber == null || receiptNumber.isBlank()) {
                throw new BusinessException(ErrorCode.REPORT_SUBMIT_FAILED, "민원 창구가 접수번호를 주지 않았습니다.");
            }
            return receiptNumber;
        } catch (RuntimeException e) {
            transactions.executeWithoutResult(status -> {
                HazardReport failed = load(reportId);
                failed.markFailed();
                announceAfterCommit(failed);
            });
            if (e instanceof BusinessException failure && failure.getErrorCode() == ErrorCode.REPORT_SUBMIT_FAILED) {
                throw failure;
            }
            log.error("신고 {} 제출 중 예상하지 못한 오류", reportId, e);
            throw new BusinessException(ErrorCode.REPORT_SUBMIT_FAILED, ErrorCode.REPORT_SUBMIT_FAILED.getMessage(), e);
        }
    }

    // 창구 구현이 시간 제한을 두지 않았더라도 요청이 한없이 묶이지 않게 여기서 끊는다.
    private String callAuthority(HazardReport report) {
        CompletableFuture<String> call = CompletableFuture.supplyAsync(() -> hazardReporter.submit(report), AUTHORITY_CALLS);
        try {
            return call.get(submitTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            throw new BusinessException(
                    ErrorCode.REPORT_SUBMIT_FAILED,
                    "민원 창구가 %d초 안에 응답하지 않았습니다. 창구에 접수됐는지 확인한 뒤 접수번호를 넣어 다시 제출해 주세요."
                            .formatted(Math.max(1, submitTimeout.toSeconds())),
                    e
            );
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.REPORT_SUBMIT_FAILED, "제출을 기다리는 중에 중단되었습니다.", e);
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
