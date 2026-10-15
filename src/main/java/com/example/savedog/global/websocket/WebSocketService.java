package com.example.savedog.global.websocket;

import com.example.savedog.Report.DTO.ReportResponse;
import com.example.savedog.Robot.DTO.DangerAlert;
import com.example.savedog.Robot.DTO.RobotConnectionEvent;
import com.example.savedog.Telemetry.DTO.TelemetryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
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
        send("/topic/robot/telemetry", telemetry);
    }

    public void broadcastStatus(RobotConnectionEvent event) {
        send("/topic/robot/status", event);
    }

    public void broadcastReport(ReportResponse report) {
        send("/topic/reports", report);
    }

    // 새 구독 주소를 추가하면 StompInboundGuard의 허용 목록에도 넣어야 화면이 구독할 수 있다.
    public void broadcastAlert(DangerAlert alert) {
        send("/topic/alerts", alert);
    }

    // 화면에 알리지 못했다고 해서 호출한 쪽의 작업(저장, 연결 처리 등)까지 실패시키지 않는다.
    private void send(String destination, Object payload) {
        try {
            simpMessagingTemplate.convertAndSend(destination, payload);
        } catch (MessagingException e) {
            log.warn("실시간 전달 실패 ({})", destination, e);
        }
    }
}
