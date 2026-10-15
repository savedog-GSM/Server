package com.example.savedog.Robot.controller;

import com.example.savedog.Robot.DTO.RobotCommandRequest;
import com.example.savedog.Robot.DTO.RobotCommandResponse;
import com.example.savedog.Robot.DTO.RobotStatusResponse;
import com.example.savedog.Robot.service.RobotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/robot")
@RequiredArgsConstructor
public class RobotController {
    private final RobotService robotService;

    @GetMapping("/status")
    public RobotStatusResponse getStatus() {
        return robotService.getStatus();
    }

    @PostMapping("/commands")
    public ResponseEntity<RobotCommandResponse> sendCommand(@RequestBody RobotCommandRequest request) {
        return ResponseEntity.accepted().body(robotService.sendCommand(request));
    }
}
