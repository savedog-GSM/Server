package com.example.savedog.Vision.controller;

import com.example.savedog.Vision.DTO.VisionResponse;
import com.example.savedog.Vision.service.VisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 사진은 POST /api/robot/frames로 받는다. (TelemetryController)
@RestController
@RequestMapping("/api/robot")
@RequiredArgsConstructor
public class VisionController {
    private final VisionService visionService;

    @GetMapping("/vision")
    public VisionResponse latest() {
        return visionService.latest()
                .map(result -> VisionResponse.of(result, visionService.isFresh(result)))
                .orElseGet(() -> VisionResponse.empty(visionService.available()));
    }
}
