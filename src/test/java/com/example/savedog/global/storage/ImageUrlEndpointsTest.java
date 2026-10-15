package com.example.savedog.global.storage;

import com.example.savedog.Report.controller.ReportController;
import com.example.savedog.Report.service.ReportService;
import com.example.savedog.Telemetry.controller.TelemetryController;
import com.example.savedog.Telemetry.service.CameraFrameService;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.Telemetry.service.TelemetryService;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// 관제 토큰을 쓰는 화면이 이미지를 띄울 때 쓰는 "주소만 받는" API
class ImageUrlEndpointsTest {
    // presigned URL은 서명이 쿼리 문자열에 들어 있어 한 글자라도 바뀌면 못 쓴다.
    private static final URI PRESIGNED = URI.create(
            "https://bucket.s3.ap-northeast-2.amazonaws.com/frames/latest.jpg?X-Amz-Expires=300&X-Amz-Signature=ab%2Fcd"
    );

    private final CameraFrameService cameraFrameService = mock(CameraFrameService.class);
    private final ReportService reportService = mock(ReportService.class);
    private final TelemetryController telemetryController = new TelemetryController(
            mock(TelemetryService.class), mock(LocationService.class), cameraFrameService
    );
    private final ReportController reportController = new ReportController(reportService);

    @Test
    @DisplayName("최신 프레임의 주소를 서명까지 그대로 돌려준다")
    void latestFrameUrl() {
        when(cameraFrameService.getLatestUrl()).thenReturn(PRESIGNED);

        assertEquals(PRESIGNED.toString(), telemetryController.getLatestFrameUrl().url());
    }

    @Test
    @DisplayName("신고 사진의 주소를 서명까지 그대로 돌려준다")
    void reportPhotoUrl() {
        when(reportService.getPhotoUrl(7L)).thenReturn(PRESIGNED);

        assertEquals(PRESIGNED.toString(), reportController.getPhotoUrl(7L).url());
    }

    @Test
    @DisplayName("이미지가 없으면 302로 넘기는 API와 같은 오류가 난다")
    void sameErrorsAsRedirectEndpoints() {
        when(cameraFrameService.getLatestUrl()).thenThrow(new BusinessException(ErrorCode.FRAME_NOT_FOUND));
        when(reportService.getPhotoUrl(7L)).thenThrow(new BusinessException(ErrorCode.FRAME_NOT_FOUND));

        assertError(ErrorCode.FRAME_NOT_FOUND, telemetryController::getLatestFrameUrl);
        assertError(ErrorCode.FRAME_NOT_FOUND, () -> reportController.getPhotoUrl(7L));
    }
}
