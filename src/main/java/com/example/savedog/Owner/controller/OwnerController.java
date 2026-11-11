package com.example.savedog.Owner.controller;

import com.example.savedog.Owner.DTO.OwnerSightingResponse;
import com.example.savedog.Owner.DTO.ReturnToOwnerResponse;
import com.example.savedog.Owner.repository.OwnerFaceRepository;
import com.example.savedog.Owner.service.OwnerSightingService;
import com.example.savedog.Owner.service.ReturnToOwnerService;
import com.example.savedog.Telemetry.DTO.LocationRequest;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 주인 찾기 (관제 토큰). 얼굴 등록·삭제는 OwnerFaceController
@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
public class OwnerController {
    private final OwnerSightingService ownerSightingService;
    private final OwnerFaceRepository ownerFaceRepository;
    private final ReturnToOwnerService returnToOwnerService;

    // 지금 카메라에 주인이 보이는지, 화면 어디쯤인지
    @GetMapping("/sighting")
    public OwnerSightingResponse sighting() {
        var latest = ownerSightingService.latest();
        boolean fresh = latest.map(ownerSightingService::isFresh).orElse(false);
        return new OwnerSightingResponse(
                ownerSightingService.available(),
                ownerFaceRepository.count(),
                fresh,
                fresh && latest.get().ownerVisible(),
                latest.orElse(null)
        );
    }

    // 주인 폰의 위치로 돌아가기를 시작한다. 이미 돌아가는 중이면 주인 위치만 바꾼다.
    // 본문은 위치 전송과 같다: {"latitude":..,"longitude":..,"accuracy":..}
    @PostMapping("/return")
    public ReturnToOwnerResponse startReturn(@RequestBody(required = false) LocationRequest request) {
        RequestValidator.required(request, "요청 본문");
        return returnToOwnerService.start(request.latitude(), request.longitude(), request.accuracy());
    }

    @GetMapping("/return")
    public ReturnToOwnerResponse returnStatus() {
        return returnToOwnerService.snapshot();
    }

    @DeleteMapping("/return")
    public ReturnToOwnerResponse stopReturn() {
        return returnToOwnerService.stop();
    }
}
