package com.example.savedog.Navigation.controller;

import com.example.savedog.Navigation.DTO.NavigationRequest;
import com.example.savedog.Navigation.DTO.NavigationResponse;
import com.example.savedog.Navigation.domain.NavigationPurpose;
import com.example.savedog.Navigation.service.RobotNavigationService;
import com.example.savedog.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 안전 경로를 찾아 그 경로로 로보독을 실제로 움직인다. (관제 토큰)
 * 출발지는 로보독에 달린 폰의 GPS다. 상태는 GET으로 1초마다 보면 된다.
 */
@RestController
@RequestMapping("/api/navigation")
@RequiredArgsConstructor
public class NavigationController {
    private final RobotNavigationService navigation;

    // 목적지로 출발한다. 이미 목적지로 가는 중이면 목적지만 바꾸고, 주인에게 돌아가던 중이면 그것을 멈추고 출발한다.
    @PostMapping
    public NavigationResponse start(@RequestBody(required = false) NavigationRequest request) {
        RequestValidator.required(request, "요청 본문");
        return navigation.start(NavigationPurpose.DESTINATION, request.latitude(), request.longitude(), null, request.mode());
    }

    // 지금 하고 있는 자동 이동(목적지 안내·주인에게 돌아가기)의 상태
    @GetMapping
    public NavigationResponse status() {
        return navigation.snapshot();
    }

    // 무엇이든 자동 이동을 멈춘다. (로보독에 STOP을 보낸다)
    @DeleteMapping
    public NavigationResponse stop() {
        return navigation.stop(null);
    }
}
