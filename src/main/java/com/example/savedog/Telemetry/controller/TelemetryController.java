package com.example.savedog.Telemetry.controller;

import com.example.savedog.Telemetry.DTO.TelemetryRequest;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
import com.example.savedog.Telemetry.service.CameraFrameService;
import com.example.savedog.Telemetry.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/robots/{robotId}")
@RequiredArgsConstructor
public class TelemetryController {
    private final TelemetryService telemetryService;
    private final CameraFrameService cameraFrameService;

    @PostMapping("/telemetry")
    public ResponseEntity<TelemetryResponse> receive(
            @PathVariable Long robotId,
            @RequestBody TelemetryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(telemetryService.receive(robotId, request));
    }

    @GetMapping("/telemetry")
    public List<TelemetryResponse> findTrack(
            @PathVariable Long robotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return telemetryService.findTrack(robotId, from, to);
    }

    @PostMapping(value = "/frames", consumes = MediaType.IMAGE_JPEG_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receiveFrame(@PathVariable Long robotId, @RequestBody byte[] jpeg) {
        cameraFrameService.store(robotId, jpeg);
    }

    @GetMapping("/frames/latest")
    public ResponseEntity<byte[]> getLatestFrame(@PathVariable Long robotId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(cameraFrameService.getLatest(robotId));
    }
}
