package com.example.savedog.Robot.websocket;

import com.example.savedog.Robot.DTO.RobotConnectionEvent;
import com.example.savedog.global.websocket.WebSocketService;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RobotSessionRegistryTest {
    private static final Duration MAX_SILENCE = Duration.ofSeconds(45);

    private final MutableClock clock = new MutableClock();
    private final WebSocketService webSocketService = mock(WebSocketService.class);
    private final RobotSessionRegistry registry = new RobotSessionRegistry(webSocketService, clock);

    @Test
    @DisplayName("접속하면 연결됨, 끊기면 끊김을 화면에 알린다")
    void announcesConnectionChanges() {
        WebSocketSession session = session("a");

        registry.register(session);
        assertTrue(registry.isConnected());

        assertTrue(registry.unregister(session));
        assertFalse(registry.isConnected());

        assertEquals(List.of(true, false), announcedStates());
    }

    @Test
    @DisplayName("새로 접속하면 이전 연결을 닫고, 밀려난 연결이 끊겨도 새 연결은 그대로다")
    void newConnectionReplacesOldOne() throws IOException {
        WebSocketSession old = session("old");
        WebSocketSession fresh = session("fresh");

        registry.register(old);
        registry.register(fresh);
        verify(old).close(CloseStatus.SESSION_NOT_RELIABLE);

        // 닫힌 이전 연결의 종료 알림이 뒤늦게 도착한 경우
        assertFalse(registry.unregister(old));
        assertTrue(registry.isConnected());
        assertEquals("fresh", registry.find().orElseThrow().getId());
    }

    @Test
    @DisplayName("응답이 이어지는 동안에는 ping만 보내고 연결을 유지한다")
    void pingsWhileAlive() throws IOException {
        WebSocketSession session = session("a");
        registry.register(session);

        for (int round = 0; round < 10; round++) {
            clock.advanceSeconds(15);
            registry.checkHeartbeat(MAX_SILENCE);
            // 로보독이 ping에 응답했다.
            registry.markAlive(session);
        }

        assertTrue(registry.isConnected());
        verify(session, times(10)).sendMessage(any(PingMessage.class));
        verify(session, never()).close(any());
    }

    @Test
    @DisplayName("45초 넘게 아무 응답이 없으면 끊긴 것으로 보고 정리한다")
    void dropsSilentConnection() throws IOException {
        WebSocketSession session = session("a");
        registry.register(session);

        clock.advanceSeconds(45);
        registry.checkHeartbeat(MAX_SILENCE);
        assertTrue(registry.isConnected());

        clock.advanceSeconds(1);
        registry.checkHeartbeat(MAX_SILENCE);

        assertFalse(registry.isConnected());
        verify(session).close(CloseStatus.SESSION_NOT_RELIABLE);
        assertEquals(List.of(true, false), announcedStates());
    }

    @Test
    @DisplayName("ping조차 보내지 못하면 바로 정리한다")
    void dropsConnectionWhenPingFails() throws IOException {
        WebSocketSession session = session("a");
        doThrow(new IOException("끊긴 연결")).when(session).sendMessage(any());
        registry.register(session);

        registry.checkHeartbeat(MAX_SILENCE);

        assertFalse(registry.isConnected());
        verify(session).close(CloseStatus.SESSION_NOT_RELIABLE);
    }

    @Test
    @DisplayName("밀려난 옛 연결에서 온 신호는 새 연결의 생존 시각을 늘리지 않는다")
    void staleSessionDoesNotKeepNewOneAlive() {
        WebSocketSession old = session("old");
        WebSocketSession fresh = session("fresh");
        registry.register(old);
        registry.register(fresh);

        clock.advanceSeconds(46);
        registry.markAlive(old);
        registry.checkHeartbeat(MAX_SILENCE);

        assertFalse(registry.isConnected());
    }

    @Test
    @DisplayName("연결이 없으면 확인할 것도 없다")
    void heartbeatWithoutConnectionDoesNothing() {
        registry.checkHeartbeat(MAX_SILENCE);

        assertFalse(registry.isConnected());
        verify(webSocketService, never()).broadcastStatus(any());
    }

    private WebSocketSession session(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        return session;
    }

    private List<Boolean> announcedStates() {
        ArgumentCaptor<RobotConnectionEvent> events = ArgumentCaptor.forClass(RobotConnectionEvent.class);
        verify(webSocketService, times(2)).broadcastStatus(events.capture());
        return events.getAllValues().stream().map(RobotConnectionEvent::connected).toList();
    }
}
