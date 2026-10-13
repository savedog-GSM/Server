package com.example.savedog.Safety.controller;

import com.example.savedog.Safety.DTO.SafetyRecordResponse;
import com.example.savedog.Safety.DTO.SafetySegmentResponse;
import com.example.savedog.Safety.service.SafetyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/safety")
@RequiredArgsConstructor
public class SafetyController {
    private final SafetyService safetyService;

    @GetMapping("/records")
    public List<SafetyRecordResponse> findInBounds(
            @RequestParam double swLat,
            @RequestParam double swLng,
            @RequestParam double neLat,
            @RequestParam double neLng
    ) {
        return safetyService.findInBounds(swLat, swLng, neLat, neLng);
    }

    @GetMapping("/segments")
    public List<SafetySegmentResponse> findSegments() {
        return safetyService.findSegments();
    }
}
