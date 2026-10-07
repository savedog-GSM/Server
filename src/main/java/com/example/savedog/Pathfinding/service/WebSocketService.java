package com.example.savedog.Pathfinding.service;

import com.example.savedog.Pathfinding.DTO.RobotTelemetry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebSocketService {
    private final SimpMessagingTemplate simpMessagingTemplate;

    public WebSocketService(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public void broadcast(RobotTelemetry telemetry) {
        simpMessagingTemplate.convertAndSend(
                "/topic/robots/" + telemetry.robotId(),
                telemetry
        );
    }
}
