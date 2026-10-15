package com.example.savedog.Robot.websocket;

import com.example.savedog.Robot.DTO.RobotConnectionEvent;
import com.example.savedog.global.websocket.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 로보독과의 연결 하나를 관리하고, 연결 상태가 바뀌면 관제 화면에 알린다.
 * 로보독은 한 대뿐이라 연결도 하나만 유지한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RobotSessionRegistry {
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    private final WebSocketService webSocketService;
    private final Clock clock;

    private final AtomicReference<WebSocketSession> current = new AtomicReference<>();
    private volatile Instant lastHeardAt;

    public void register(WebSocketSession session) {
        // 여러 스레드가 동시에 명령을 보내도 안전하도록 감싼다.
        WebSocketSession previous = current.getAndSet(
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES)
        );
        lastHeardAt = clock.instant();
        // 로보독이 재부팅 후 다시 붙으면 이전 연결이 남아 있을 수 있다.
        if (previous != null) {
            closeQuietly(previous, CloseStatus.SESSION_NOT_RELIABLE);
        }
        webSocketService.broadcastStatus(RobotConnectionEvent.of(true));
        log.info("로보독 연결");
    }

    // 현재 등록된 세션일 때만 제거한다. 재접속으로 밀려난 옛 세션이 새 세션을 지우지 않게 하기 위함.
    public boolean unregister(WebSocketSession session) {
        WebSocketSession registered = current.get();
        if (registered == null || !registered.getId().equals(session.getId())) {
            return false;
        }
        if (!current.compareAndSet(registered, null)) {
            return false;
        }
        webSocketService.broadcastStatus(RobotConnectionEvent.of(false));
        log.info("로보독 연결 종료");
        return true;
    }

    // 더 쓸 수 없게 된 연결을 정리한다. 상태부터 "끊김"으로 바꾼 뒤 소켓을 닫는다.
    public void discard(WebSocketSession session, CloseStatus status) {
        unregister(session);
        closeQuietly(session, status);
    }

    // 로보독에서 무엇이든(메시지, ping 응답) 받았을 때 부른다.
    public void markAlive(WebSocketSession session) {
        WebSocketSession registered = current.get();
        if (registered != null && registered.getId().equals(session.getId())) {
            lastHeardAt = clock.instant();
        }
    }

    /**
     * 전원이 꺼지거나 통신이 끊긴 로보독은 소켓이 열린 채로 남을 수 있다.
     * 주기적으로 ping을 보내고, 응답이 maxSilence 넘게 없으면 끊긴 것으로 본다.
     */
    public void checkHeartbeat(Duration maxSilence) {
        WebSocketSession session = current.get();
        if (session == null) {
            return;
        }
        if (Duration.between(lastHeardAt, clock.instant()).compareTo(maxSilence) > 0) {
            log.warn("로보독이 {}초 넘게 응답하지 않아 연결을 끊습니다.", maxSilence.toSeconds());
            discard(session, CloseStatus.SESSION_NOT_RELIABLE);
            return;
        }
        try {
            session.sendMessage(new PingMessage());
        } catch (IOException | RuntimeException e) {
            log.warn("로보독에 ping을 보내지 못해 연결을 끊습니다: {}", e.toString());
            discard(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    public Optional<WebSocketSession> find() {
        return Optional.ofNullable(current.get());
    }

    public boolean isConnected() {
        return current.get() != null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException | RuntimeException e) {
            log.debug("로보독 세션 종료 실패", e);
        }
    }
}
