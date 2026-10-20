package com.example.savedog.Report.service;

import com.example.savedog.Report.DTO.ReportResponse;
import com.example.savedog.Report.DTO.ReportSubmitRequest;
import com.example.savedog.Report.address.AddressResolver;
import com.example.savedog.Report.domain.HazardReport;
import com.example.savedog.Report.domain.ReportStatus;
import com.example.savedog.Report.reporter.HazardReporter;
import com.example.savedog.Report.reporter.UnconfiguredHazardReporter;
import com.example.savedog.Report.repository.HazardReportRepository;
import com.example.savedog.Safety.analyzer.SafetyAssessment;
import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.Telemetry.domain.RobotLocation;
import com.example.savedog.Telemetry.domain.Telemetry;
import com.example.savedog.Telemetry.service.CameraFrameService;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.storage.ImageStorage;
import com.example.savedog.global.websocket.WebSocketService;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {
    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.9780;
    private static final double METERS_PER_LATITUDE_DEGREE = 111_320;

    private final MutableClock clock = new MutableClock();
    private final HazardReportRepository repository = mock(HazardReportRepository.class);
    private final AddressResolver addressResolver = mock(AddressResolver.class);
    private final CameraFrameService cameraFrameService = mock(CameraFrameService.class);
    private final ImageStorage imageStorage = mock(ImageStorage.class);
    private final WebSocketService webSocketService = mock(WebSocketService.class);
    // DB 없이 트랜잭션 경계만 흉내 낸다. 콜백이 도는 동안 inTransaction이 true다.
    private final AtomicBoolean inTransaction = new AtomicBoolean();
    private final TransactionOperations transactions = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            inTransaction.set(true);
            try {
                return action.doInTransaction(null);
            } finally {
                inTransaction.set(false);
            }
        }
    };

    private ReportService serviceWith(HazardReporter reporter) {
        when(repository.save(any())).thenAnswer(invocation -> {
            HazardReport saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", 7L);
            }
            return saved;
        });
        return new ReportService(
                repository, reporter, addressResolver, cameraFrameService, imageStorage, webSocketService, transactions, clock
        );
    }

    @Test
    @DisplayName("장애물로 판단된 위험은 주소와 설명을 담아 승인 대기 신고로 만든다")
    void createsObstacleReport() {
        when(addressResolver.resolve(anyDouble(), anyDouble())).thenReturn(Optional.of("서울 중구 세종대로 110"));
        when(cameraFrameService.findRecentKey()).thenReturn(Optional.empty());
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        ReportResponse report = service.createIfNew(telemetry(18), obstacle(), record(0)).orElseThrow();

        assertEquals(HazardType.OBSTACLE, report.hazardType());
        assertEquals(ReportStatus.PENDING, report.status());
        assertEquals("서울 중구 세종대로 110", report.address());
        assertEquals("보행로에 장애물이 있습니다. 로보독 전방 18cm에서 감지.", report.description());
        assertEquals(5, report.risk().score());
        assertFalse(report.hasPhoto());
        verify(webSocketService).broadcastReport(report);
    }

    @Test
    @DisplayName("위험 유형이 없는 판단은 기타로 만들고 판정 근거를 설명에 담는다")
    void createsEtcReportWhenTypeIsUnknown() {
        when(addressResolver.resolve(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        when(cameraFrameService.findRecentKey()).thenReturn(Optional.empty());
        ReportService service = serviceWith(new UnconfiguredHazardReporter());
        SafetyAssessment unknown = new SafetyAssessment(SafetyLevel.DANGER, 5, "판단 근거", null);

        ReportResponse report = service.createIfNew(telemetry(18), unknown, record(0)).orElseThrow();

        assertEquals(HazardType.ETC, report.hazardType());
        assertEquals("위험 구간이 감지되었습니다. (판단 근거)", report.description());
        assertNull(report.address());
    }

    @Test
    @DisplayName("방금 찍힌 프레임이 있으면 신고용 키로 복사해 붙인다")
    void attachesRecentFrame() {
        when(addressResolver.resolve(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        when(cameraFrameService.findRecentKey()).thenReturn(Optional.of("frames/latest.jpg"));
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        ReportResponse report = service.createIfNew(telemetry(18), obstacle(), record(0)).orElseThrow();

        assertTrue(report.hasPhoto());
        verify(imageStorage).copy("frames/latest.jpg", "reports/7.jpg");
    }

    @Test
    @DisplayName("사진을 붙이지 못해도 신고는 사진 없이 만들어진다")
    void photoFailureDoesNotBlockReport() {
        when(addressResolver.resolve(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        when(cameraFrameService.findRecentKey()).thenReturn(Optional.of("frames/latest.jpg"));
        doThrow(new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE)).when(imageStorage).copy(anyString(), anyString());
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        ReportResponse report = service.createIfNew(telemetry(18), obstacle(), record(0)).orElseThrow();

        assertFalse(report.hasPhoto());
        assertEquals(ReportStatus.PENDING, report.status());
    }

    @Test
    @DisplayName("30m 안에 24시간 이내 신고가 있으면 새로 만들지 않는다")
    void skipsDuplicateNearby() {
        when(repository.findByCreatedAtAfter(any())).thenReturn(List.of(existingReport(20)));
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        assertTrue(service.createIfNew(telemetry(18), obstacle(), record(0)).isEmpty());
        verify(repository, never()).save(any());
        verify(webSocketService, never()).broadcastReport(any());
    }

    @Test
    @DisplayName("최근 신고가 30m보다 멀리 있으면 새로 만든다")
    void createsWhenExistingReportIsFarAway() {
        when(repository.findByCreatedAtAfter(any())).thenReturn(List.of(existingReport(40)));
        when(addressResolver.resolve(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        when(cameraFrameService.findRecentKey()).thenReturn(Optional.empty());
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        assertTrue(service.createIfNew(telemetry(18), obstacle(), record(0)).isPresent());
    }

    @Test
    @DisplayName("접수번호를 주면 창구로 보내지 않고 제출 완료로 기록한다")
    void submitWithReceiptNumber() {
        HazardReporter reporter = mock(HazardReporter.class);
        HazardReport report = existingReport(0);
        when(repository.findById(7L)).thenReturn(Optional.of(report));
        ReportService service = serviceWith(reporter);

        ReportResponse response = service.submit(7L, new ReportSubmitRequest("  2026-안전-000123  "));

        assertEquals(ReportStatus.SUBMITTED, response.status());
        assertEquals("2026-안전-000123", response.receiptNumber());
        assertNotNull(response.submittedAt());
        verify(reporter, never()).submit(any());
    }

    @Test
    @DisplayName("접수번호 없이 제출하면 창구로 보내고 받은 접수번호를 기록한다")
    void submitThroughReporter() {
        HazardReporter reporter = mock(HazardReporter.class);
        when(reporter.submit(any())).thenReturn("R-42");
        when(repository.findById(7L)).thenReturn(Optional.of(existingReport(0)));
        ReportService service = serviceWith(reporter);

        ReportResponse response = service.submit(7L, null);

        assertEquals(ReportStatus.SUBMITTED, response.status());
        assertEquals("R-42", response.receiptNumber());
    }

    @Test
    @DisplayName("창구 전송에 실패하면 제출 실패 상태로 남기고 REPORT_SUBMIT_FAILED")
    void submitFailureMarksFailed() {
        HazardReport report = existingReport(0);
        when(repository.findById(7L)).thenReturn(Optional.of(report));
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        assertError(ErrorCode.REPORT_SUBMIT_FAILED, () -> service.submit(7L, null));
        assertEquals(ReportStatus.FAILED, report.getStatus());

        // 실패한 신고는 접수번호를 넣어 다시 제출할 수 있다.
        ReportResponse retried = service.submit(7L, new ReportSubmitRequest("R-1"));
        assertEquals(ReportStatus.SUBMITTED, retried.status());
    }

    @Test
    @DisplayName("창구 연동부가 예상하지 못한 오류를 내도 REPORT_SUBMIT_FAILED로 바꿔 준다")
    void unexpectedReporterErrorIsWrapped() {
        HazardReporter reporter = mock(HazardReporter.class);
        when(reporter.submit(any())).thenThrow(new IllegalStateException("연결 끊김"));
        HazardReport report = existingReport(0);
        when(repository.findById(7L)).thenReturn(Optional.of(report));
        ReportService service = serviceWith(reporter);

        assertError(ErrorCode.REPORT_SUBMIT_FAILED, () -> service.submit(7L, null));
        assertEquals(ReportStatus.FAILED, report.getStatus());
    }

    @Test
    @DisplayName("민원 창구로 보내는 동안에는 DB 트랜잭션을 열어 두지 않는다")
    void callsAuthorityOutsideTransaction() {
        HazardReporter reporter = mock(HazardReporter.class);
        AtomicBoolean calledInTransaction = new AtomicBoolean(true);
        when(reporter.submit(any())).thenAnswer(invocation -> {
            calledInTransaction.set(inTransaction.get());
            return "R-42";
        });
        when(repository.findById(7L)).thenReturn(Optional.of(existingReport(0)));
        ReportService service = serviceWith(reporter);

        assertEquals(ReportStatus.SUBMITTED, service.submit(7L, null).status());
        assertFalse(calledInTransaction.get());
    }

    @Test
    @DisplayName("창구로 보내는 중인 신고는 다른 요청이 제출하거나 기각할 수 없고, 창구에는 한 번만 보낸다")
    void blocksConcurrentProcessing() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HazardReporter reporter = mock(HazardReporter.class);
        when(reporter.submit(any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "R-1";
        });
        when(repository.findById(7L)).thenReturn(Optional.of(existingReport(0)));
        ReportService service = serviceWith(reporter);

        CompletableFuture<ReportResponse> first = CompletableFuture.supplyAsync(() -> service.submit(7L, null));
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        BusinessException busy = assertError(ErrorCode.REPORT_ALREADY_PROCESSED,
                () -> service.submit(7L, new ReportSubmitRequest("R-2")));
        assertEquals("이 신고는 지금 다른 요청이 처리하는 중입니다.", busy.getMessage());
        assertError(ErrorCode.REPORT_ALREADY_PROCESSED, () -> service.dismiss(7L));

        release.countDown();
        assertEquals(ReportStatus.SUBMITTED, first.get(5, TimeUnit.SECONDS).status());
        verify(reporter, times(1)).submit(any());
        // 처리가 끝나면 '처리 중'이 풀린다. (이미 제출된 신고라 409지만 그 이유는 상태 때문)
        BusinessException done = assertError(ErrorCode.REPORT_ALREADY_PROCESSED, () -> service.dismiss(7L));
        assertEquals(ErrorCode.REPORT_ALREADY_PROCESSED.getMessage(), done.getMessage());
    }

    @Test
    @DisplayName("창구가 시간 안에 답하지 않으면 제출 실패로 남기고, 접수번호로 다시 제출할 수 있다")
    void timesOutSlowAuthority() {
        CountDownLatch slow = new CountDownLatch(1);
        HazardReporter reporter = mock(HazardReporter.class);
        when(reporter.submit(any())).thenAnswer(invocation -> {
            slow.await(5, TimeUnit.SECONDS);
            return "늦은 접수번호";
        });
        HazardReport report = existingReport(0);
        when(repository.findById(7L)).thenReturn(Optional.of(report));
        ReportService service = serviceWith(reporter);
        service.setSubmitTimeout(Duration.ofMillis(200));

        BusinessException timeout = assertError(ErrorCode.REPORT_SUBMIT_FAILED, () -> service.submit(7L, null));
        assertTrue(timeout.getMessage().contains("응답하지 않았습니다"), timeout.getMessage());
        assertEquals(ReportStatus.FAILED, report.getStatus());

        assertEquals(ReportStatus.SUBMITTED, service.submit(7L, new ReportSubmitRequest("R-9")).status());
        slow.countDown();
    }

    @Test
    @DisplayName("이미 제출했거나 기각한 신고는 다시 처리할 수 없다")
    void processedReportCannotBeProcessedAgain() {
        HazardReport report = existingReport(0);
        when(repository.findById(7L)).thenReturn(Optional.of(report));
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        service.dismiss(7L);
        assertEquals(ReportStatus.DISMISSED, report.getStatus());

        assertError(ErrorCode.REPORT_ALREADY_PROCESSED, () -> service.dismiss(7L));
        assertError(ErrorCode.REPORT_ALREADY_PROCESSED, () -> service.submit(7L, new ReportSubmitRequest("R-1")));
        assertEquals(ReportStatus.DISMISSED, report.getStatus());
    }

    @Test
    @DisplayName("없는 신고는 REPORT_NOT_FOUND")
    void unknownReport() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        assertError(ErrorCode.REPORT_NOT_FOUND, () -> service.find(99L));
        assertError(ErrorCode.REPORT_NOT_FOUND, () -> service.submit(99L, null));
        assertError(ErrorCode.REPORT_NOT_FOUND, () -> service.dismiss(99L));
        assertError(ErrorCode.REPORT_NOT_FOUND, () -> service.getPhotoUrl(99L));
    }

    @Test
    @DisplayName("빈 접수번호나 너무 긴 접수번호는 거절한다")
    void rejectsBadReceiptNumber() {
        HazardReport report = existingReport(0);
        when(repository.findById(7L)).thenReturn(Optional.of(report));
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        assertError(ErrorCode.INVALID_REQUEST, () -> service.submit(7L, new ReportSubmitRequest("   ")));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.submit(7L, new ReportSubmitRequest("x".repeat(101))));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.submit(7L, new ReportSubmitRequest("R-1\n위조된 줄")));
        assertEquals(ReportStatus.PENDING, report.getStatus());
    }

    @Test
    @DisplayName("주소 변환부가 예상하지 못한 오류를 내도 신고는 주소 없이 만들어진다")
    void addressFailureDoesNotBlockReport() {
        when(addressResolver.resolve(anyDouble(), anyDouble())).thenThrow(new IllegalStateException("연결 끊김"));
        when(cameraFrameService.findRecentKey()).thenReturn(Optional.empty());
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        ReportResponse report = service.createIfNew(telemetry(18), obstacle(), record(0)).orElseThrow();

        assertNull(report.address());
        assertEquals(ReportStatus.PENDING, report.status());
    }

    @Test
    @DisplayName("사진이 없는 신고의 사진을 달라고 하면 FRAME_NOT_FOUND, 있으면 저장소 주소")
    void photoUrl() {
        HazardReport report = existingReport(0);
        when(repository.findById(7L)).thenReturn(Optional.of(report));
        when(imageStorage.presignedUrl(anyString(), any())).thenReturn(URI.create("https://storage.test/reports/7.jpg"));
        ReportService service = serviceWith(new UnconfiguredHazardReporter());

        assertError(ErrorCode.FRAME_NOT_FOUND, () -> service.getPhotoUrl(7L));

        report.attachPhoto("reports/7.jpg");
        assertEquals(URI.create("https://storage.test/reports/7.jpg"), service.getPhotoUrl(7L));
    }

    private SafetyAssessment obstacle() {
        return new SafetyAssessment(SafetyLevel.DANGER, 5, "전방 18cm 장애물", HazardType.OBSTACLE);
    }

    private Telemetry telemetry(double distance) {
        return new Telemetry(new RobotLocation(LATITUDE, LONGITUDE, 5.0, 0.0, LocalDateTime.now(clock)), distance);
    }

    private SafetyRecord record(double northMeters) {
        return new SafetyRecord(
                1L, LATITUDE + northMeters / METERS_PER_LATITUDE_DEGREE, LONGITUDE, SafetyLevel.DANGER, 5, "전방 18cm 장애물"
        );
    }

    private HazardReport existingReport(double northMeters) {
        HazardReport report = new HazardReport(
                1L, HazardType.OBSTACLE, LATITUDE + northMeters / METERS_PER_LATITUDE_DEGREE, LONGITUDE,
                null, "보행로에 장애물이 있습니다.", SafetyLevel.DANGER, 5, "전방 18cm 장애물"
        );
        ReflectionTestUtils.setField(report, "id", 7L);
        return report;
    }
}
