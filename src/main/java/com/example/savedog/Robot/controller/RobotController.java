package com.example.savedog.Robot.controller;

import com.example.savedog.Robot.DTO.RobotCommandRequest;
import com.example.savedog.Robot.DTO.RobotCommandResponse;
import com.example.savedog.Robot.DTO.RobotStatusResponse;
import com.example.savedog.Robot.service.RobotEventService;
import com.example.savedog.Robot.service.RobotService;
import com.example.savedog.global.security.DeviceEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/robot")
@RequiredArgsConstructor
public class RobotController {
    private final RobotService robotService;
    private final RobotEventService robotEventService;

    @GetMapping("/status")
    public RobotStatusResponse getStatus() {
        return robotService.getStatus();
    }

    @PostMapping("/commands")
    public ResponseEntity<RobotCommandResponse> sendCommand(@RequestBody RobotCommandRequest request) {
        return ResponseEntity.accepted().body(robotService.sendCommand(request));
    }

    // 중계 프로그램이 QR 인식 같은 이벤트를 알린다. 화면에는 /topic/robot/events로 전달된다.
    @DeviceEndpoint
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receiveEvent(@RequestBody(required = false) Map<String, Object> event) {
        robotEventService.receive(event);
    }
}
