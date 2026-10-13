package com.example.savedog.global.websocket;

import com.example.savedog.Robot.DTO.RobotConnectionEvent;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
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

    public void broadcastTelemetry(TelemetryResponse telemetry) {
        simpMessagingTemplate.convertAndSend("/topic/robot/telemetry", telemetry);
    }

    public void broadcastStatus(RobotConnectionEvent event) {
        simpMessagingTemplate.convertAndSend("/topic/robot/status", event);
    }
}
