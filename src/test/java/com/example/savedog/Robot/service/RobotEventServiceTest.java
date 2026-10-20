package com.example.savedog.Robot.service;

import com.example.savedog.Robot.DTO.RobotEventMessage;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.websocket.WebSocketService;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RobotEventServiceTest {
    private final MutableClock clock = new MutableClock();
    private final WebSocketService webSocketService = mock(WebSocketService.class);
    private final RobotEventService service = new RobotEventService(webSocketService, clock);

    @Test
    @DisplayName("받은 이벤트를 그대로 싣고 받은 시각을 붙여 화면에 전달한다")
    void broadcastsEventAsReceived() {
        Map<String, Object> event = Map.of("qrCode", "left", "robotId", "robodog-01");

        service.receive(event);

        ArgumentCaptor<RobotEventMessage> captor = ArgumentCaptor.forClass(RobotEventMessage.class);
        verify(webSocketService).broadcastRobotEvent(captor.capture());
        assertEquals(event, captor.getValue().event());
        assertEquals(LocalDateTime.now(clock), captor.getValue().receivedAt());
    }

    @Test
    @DisplayName("비었거나 항목이 너무 많은 이벤트는 거절한다")
    void rejectsEmptyOrHugeEvents() {
        assertError(ErrorCode.INVALID_REQUEST, () -> service.receive(null));
        assertError(ErrorCode.INVALID_REQUEST, () -> service.receive(Map.of()));
        Map<String, Object> huge = new HashMap<>();
        for (int i = 0; i < 51; i++) {
            huge.put("k" + i, i);
        }
        assertError(ErrorCode.INVALID_REQUEST, () -> service.receive(huge));
        verify(webSocketService, never()).broadcastRobotEvent(any());
    }
}
