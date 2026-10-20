package com.example.savedog.Report.controller;

import com.example.savedog.Report.DTO.HazardUploadRequest;
import com.example.savedog.Report.DTO.HazardUploadResult;
import com.example.savedog.Report.DTO.ReportResponse;
import com.example.savedog.Report.service.HazardUploadService;
import com.example.savedog.global.security.DeviceEndpoint;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/robot")
@RequiredArgsConstructor
public class HazardUploadController {
    private final HazardUploadService hazardUploadService;

    // 중계 프로그램이 위험 QR을 보고 찍은 사진과 정보를 보낸다.
    // 새 신고면 201, 근처에 같은 종류의 최근 신고가 있으면 그 신고를 200으로 돌려준다.
    // detectedAt, roll, pitch도 함께 오지만 쓰지 않는다. (시각은 서버가 받은 시각, 기울기는 뺌)
    @DeviceEndpoint
    @PostMapping(value = "/hazards", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReportResponse> upload(
            @RequestParam(required = false) String hazardType,
            @RequestParam(required = false) String robotId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String qrCode,
            @RequestPart(required = false) MultipartFile image
    ) {
        HazardUploadResult result = hazardUploadService.upload(
                new HazardUploadRequest(hazardType, robotId, source, qrCode, bytesOf(image))
        );
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.report());
    }

    private byte[] bytesOf(MultipartFile image) {
        if (image == null) {
            return null;
        }
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw RequestValidator.invalid("image를 읽지 못했습니다.");
        }
    }
}
