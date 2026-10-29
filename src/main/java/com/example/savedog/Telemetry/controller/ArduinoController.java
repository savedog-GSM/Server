package com.example.savedog.Telemetry.controller;

import com.example.savedog.Telemetry.DTO.SensorValues;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
import com.example.savedog.Telemetry.service.TelemetryService;
import com.example.savedog.global.security.DeviceEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 아두이노(초음파 + 온습도 센서) 전용 주소. 본문: {"distance":123.4,"temperature":24.5,"humidity":41.0}
// 받은 값은 /api/robot/telemetry와 똑같이 저장·위험도 판단·실시간 전달된다.
@RestController
@RequestMapping("/api/arduino")
@RequiredArgsConstructor
public class ArduinoController {
    private final TelemetryService telemetryService;

    @DeviceEndpoint
    @PostMapping("/sensors")
    public ResponseEntity<TelemetryResponse> receive(@RequestBody(required = false) SensorValues values) {
        return ResponseEntity.status(HttpStatus.CREATED).body(telemetryService.receiveArduino(values));
    }
}
