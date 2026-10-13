package com.example.savedog.Robot.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

// 로보독은 한 대뿐이라 연결도 하나만 유지한다.
@Slf4j
@Component
public class RobotSessionRegistry {
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 64 * 1024;

    private final AtomicReference<WebSocketSession> current = new AtomicReference<>();

    public void register(WebSocketSession session) {
        // 여러 스레드가 동시에 명령을 보내도 안전하도록 감싼다.
        WebSocketSession previous = current.getAndSet(
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES)
        );
        // 로보독이 재부팅 후 다시 붙으면 이전 연결이 남아 있을 수 있다.
        if (previous != null) {
            closeQuietly(previous);
        }
    }

    // 현재 등록된 세션일 때만 제거한다. 재접속으로 밀려난 옛 세션이 새 세션을 지우지 않게 하기 위함.
    public boolean unregister(WebSocketSession session) {
        WebSocketSession registered = current.get();
        if (registered == null || !registered.getId().equals(session.getId())) {
            return false;
        }
        return current.compareAndSet(registered, null);
    }

    public Optional<WebSocketSession> find() {
        return Optional.ofNullable(current.get());
    }

    public boolean isConnected() {
        return current.get() != null;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException e) {
            log.debug("이전 로보독 세션 종료 실패", e);
        }
    }
}
