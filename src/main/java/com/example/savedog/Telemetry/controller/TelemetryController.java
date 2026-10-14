package com.example.savedog.Telemetry.controller;

import com.example.savedog.Telemetry.DTO.LocationRequest;
import com.example.savedog.Telemetry.DTO.TelemetryRequest;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
import com.example.savedog.Telemetry.service.CameraFrameService;
import com.example.savedog.Telemetry.service.LocationService;
import com.example.savedog.Telemetry.service.TelemetryService;
import com.example.savedog.global.security.DeviceEndpoint;
import com.example.savedog.global.storage.ImageUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/robot")
@RequiredArgsConstructor
public class TelemetryController {
    private final TelemetryService telemetryService;
    private final LocationService locationService;
    private final CameraFrameService cameraFrameService;

    @DeviceEndpoint
    @PostMapping("/telemetry")
    public ResponseEntity<TelemetryResponse> receive(@RequestBody TelemetryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(telemetryService.receive(request));
    }

    @GetMapping("/telemetry")
    public List<TelemetryResponse> findTrack(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return telemetryService.findTrack(from, to);
    }

    @DeviceEndpoint
    @PostMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receiveLocation(@RequestBody LocationRequest request) {
        locationService.store(request);
    }

    @DeviceEndpoint
    @PostMapping(value = "/frames", consumes = MediaType.IMAGE_JPEG_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receiveFrame(@RequestBody(required = false) byte[] jpeg) {
        cameraFrameService.store(jpeg);
    }

    // 이미지는 S3에 있으므로 presigned URL로 넘긴다.
    @GetMapping("/frames/latest")
    public ResponseEntity<Void> getLatestFrame() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(cameraFrameService.getLatestUrl())
                .build();
    }

    // <img>는 인증 헤더를 붙일 수 없어서, 관제 토큰을 쓰는 화면은 주소만 받아 img.src에 넣는다.
    @GetMapping("/frames/latest/url")
    public ImageUrlResponse getLatestFrameUrl() {
        return ImageUrlResponse.from(cameraFrameService.getLatestUrl());
    }
}
