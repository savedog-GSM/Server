package com.example.savedog.Report.service;

import com.example.savedog.Report.DTO.HazardUploadRequest;
import com.example.savedog.Report.DTO.HazardUploadResult;
import com.example.savedog.Report.DTO.ReportResponse;
import com.example.savedog.Report.address.AddressResolver;
import com.example.savedog.Report.domain.HazardReport;
import com.example.savedog.Report.domain.ReportStatus;
import com.example.savedog.Report.repository.HazardReportRepository;
import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Telemetry.DTO.LocationRequest;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.storage.ImageStorage;
import com.example.savedog.global.websocket.WebSocketService;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HazardUploadServiceTest {
    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.9780;
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    private final MutableClock clock = new MutableClock();
    private final HazardReportRepository repository = mock(HazardReportRepository.class);
    private final LocationService locationService = new LocationService(clock);
    private final AddressResolver addressResolver = mock(AddressResolver.class);
    private final ImageStorage imageStorage = mock(ImageStorage.class);
    private final WebSocketService webSocketService = mock(WebSocketService.class);
    private final AtomicLong ids = new AtomicLong();
    private final HazardUploadService service = new HazardUploadService(
            repository, locationService, addressResolver, imageStorage, webSocketService, clock
    );

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(invocation -> {
            HazardReport saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", ids.incrementAndGet());
            }
            return saved;
        });
        when(repository.findByCreatedAtAfter(any())).thenReturn(List.of());
        when(addressResolver.resolve(anyDouble(), anyDouble())).thenReturn(Optional.of("서울 중구 세종대로 110"));
    }

    @Test
    @DisplayName("화재 QR 신고는 최근 위치·주소·사진을 담은 승인 대기 신고가 되고 화면에 알린다")
    void createsFireReportWithPhoto() {
        phoneAt(LATITUDE, LONGITUDE);

        HazardUploadResult result = service.upload(new HazardUploadRequest("FIRE", "robodog-01", "QR_DEMO", "fire", JPEG));

        ReportResponse report = result.report();
        assertTrue(result.created());
        assertEquals(HazardType.FIRE, report.hazardType());
        assertEquals(ReportStatus.PENDING, report.status());
        assertEquals(LATITUDE, report.latitude());
        assertEquals("서울 중구 세종대로 110", report.address());
        assertEquals("화재가 감지되었습니다. 로보독이 QR(fire)을 인식해 신고했습니다.", report.description());
        assertEquals(SafetyLevel.DANGER, report.risk().level());
        assertEquals(5, report.risk().score());
        assertEquals("화재 (QR fire)", report.risk().reason());
        assertTrue(report.hasPhoto());
        verify(imageStorage).put("reports/1.jpg", JPEG, "image/jpeg");
        verify(webSocketService).broadcastReport(report);
    }

    @Test
    @DisplayName("중계 프로그램의 유형 이름을 서버의 유형으로 바꾼다 (대소문자·공백 무시)")
    void mapsRelayHazardTypes() {
        phoneAt(LATITUDE, LONGITUDE);

        assertEquals(HazardType.ROUGH_ROAD, service.upload(request(" ground_damage ", null)).report().hazardType());
        assertEquals(HazardType.ETC, service.upload(request("GENERAL_DANGER", null)).report().hazardType());
    }

    @Test
    @DisplayName("사진이 없어도 신고는 만든다")
    void createsReportWithoutPhoto() {
        phoneAt(LATITUDE, LONGITUDE);

        ReportResponse report = service.upload(new HazardUploadRequest("GROUND_DAMAGE", null, null, null, null)).report();

        assertFalse(report.hasPhoto());
        assertEquals("노면이 파손되어 있습니다. 로보독이 현장에서 신고했습니다.", report.description());
        verify(imageStorage, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("사진을 저장하지 못해도(S3 미설정 등) 신고는 사진 없이 남는다")
    void keepsReportWhenPhotoCannotBeStored() {
        phoneAt(LATITUDE, LONGITUDE);
        doThrow(new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE)).when(imageStorage).put(any(), any(), any());

        HazardUploadResult result = service.upload(request("FIRE", JPEG));

        assertTrue(result.created());
        assertFalse(result.report().hasPhoto());
        verify(webSocketService).broadcastReport(result.report());
    }

    @Test
    @DisplayName("30m 안에 같은 종류의 최근 신고가 있으면 새로 만들지 않고 그 신고를 돌려준다")
    void returnsRecentDuplicate() {
        phoneAt(LATITUDE, LONGITUDE);
        HazardReport existing = report(HazardType.FIRE, LATITUDE + 0.0001, LONGITUDE);
        when(repository.findByCreatedAtAfter(any())).thenReturn(List.of(existing));

        HazardUploadResult result = service.upload(request("FIRE", JPEG));

        assertFalse(result.created());
        assertEquals(existing.getId(), result.report().id());
        verify(repository, never()).save(any());
        verify(imageStorage, never()).put(any(), any(), any());
        verify(webSocketService, never()).broadcastReport(any());
    }

    @Test
    @DisplayName("근처의 다른 종류 신고나 멀리 있는 같은 종류 신고는 중복으로 보지 않는다")
    void differentTypeOrFarAwayIsNotDuplicate() {
        phoneAt(LATITUDE, LONGITUDE);
        when(repository.findByCreatedAtAfter(any())).thenReturn(List.of(
                report(HazardType.OBSTACLE, LATITUDE, LONGITUDE),
                report(HazardType.FIRE, LATITUDE + 0.001, LONGITUDE)  // 약 111m
        ));

        assertTrue(service.upload(request("FIRE", null)).created());
    }

    @Test
    @DisplayName("최근 60초 안에 받은 위치가 없으면 LOCATION_UNAVAILABLE이고 아무것도 저장하지 않는다")
    void needsRecentLocation() {
        assertError(ErrorCode.LOCATION_UNAVAILABLE, () -> service.upload(request("FIRE", JPEG)));

        phoneAt(LATITUDE, LONGITUDE);
        clock.advanceSeconds(61);
        assertError(ErrorCode.LOCATION_UNAVAILABLE, () -> service.upload(request("FIRE", JPEG)));

        verify(repository, never()).save(any());
        verify(imageStorage, never()).put(any(), any(), any());
    }

    @Test
    @DisplayName("멈춰 선 뒤 조금 지난 위치(60초 이내)는 쓴다")
    void acceptsLocationWithinSixtySeconds() {
        phoneAt(LATITUDE, LONGITUDE);
        clock.advanceSeconds(59);

        assertTrue(service.upload(request("FIRE", null)).created());
    }

    @Test
    @DisplayName("유형이 없거나 모르는 유형, JPEG가 아닌 사진, 빈 사진, 제어 문자가 든 항목은 거절한다")
    void rejectsInvalidInput() {
        phoneAt(LATITUDE, LONGITUDE);

        assertError(ErrorCode.INVALID_REQUEST, () -> service.upload(request(null, null)));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.upload(request(" ", null)));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.upload(request("FLOOD", null)));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.upload(request("FIRE", new byte[]{1, 2, 3, 4})));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.upload(request("FIRE", new byte[0])));
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.upload(new HazardUploadRequest("FIRE", null, null, "fire\nfake", null)));
        assertError(ErrorCode.INVALID_REQUEST,
                () -> service.upload(new HazardUploadRequest("FIRE", "x".repeat(65), null, null, null)));
        assertError(ErrorCode.PAYLOAD_TOO_LARGE,
                () -> service.upload(request("FIRE", bigJpeg(2 * 1024 * 1024 + 1))));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("주소를 구하다 실패해도 주소 없이 신고를 만든다")
    void createsReportWithoutAddressWhenLookupFails() {
        phoneAt(LATITUDE, LONGITUDE);
        when(addressResolver.resolve(anyDouble(), anyDouble())).thenThrow(new IllegalStateException("카카오 장애"));

        ReportResponse report = service.upload(request("FIRE", null)).report();

        assertNull(report.address());
        verify(repository).save(any());
        verify(addressResolver).resolve(eq(LATITUDE), eq(LONGITUDE));
    }

    private void phoneAt(double latitude, double longitude) {
        locationService.store(new LocationRequest(latitude, longitude, 5.0));
    }

    private HazardUploadRequest request(String hazardType, byte[] image) {
        return new HazardUploadRequest(hazardType, "robodog-01", "QR_DEMO", null, image);
    }

    private HazardReport report(HazardType type, double latitude, double longitude) {
        HazardReport report = new HazardReport(null, type, latitude, longitude, null, "기존 신고", SafetyLevel.DANGER, 5, null);
        ReflectionTestUtils.setField(report, "id", 100L + ids.incrementAndGet());
        ReflectionTestUtils.setField(report, "createdAt", LocalDateTime.now(clock).minusHours(1));
        return report;
    }

    private byte[] bigJpeg(int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(JPEG, 0, bytes, 0, JPEG.length);
        return bytes;
    }
}
