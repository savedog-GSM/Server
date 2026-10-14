package com.example.savedog.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class StompInboundGuardTest {
    private static final String OPERATOR = "operator-token-0123456789";

    private final MessageChannel channel = mock(MessageChannel.class);
    // 한 접속에서 오는 프레임들은 같은 세션 속성을 공유한다.
    private final Map<String, Object> session = new HashMap<>();

    @Test
    @DisplayName("화면이 보내는 SEND는 항상 거절한다")
    void rejectsSend() {
        StompInboundGuard guard = guardWithoutToken();

        assertRejected(guard, frame(StompCommand.SEND, "/topic/robot/telemetry", null));
        assertRejected(guard, frame(StompCommand.SEND, "/topic/reports", null));
        assertRejected(guard, frame(StompCommand.SEND, "/app/anything", null));
    }

    @Test
    @DisplayName("정해진 주소만 구독할 수 있다")
    void allowsOnlyKnownSubscriptions() {
        StompInboundGuard guard = guardWithoutToken();

        assertPassed(guard, frame(StompCommand.SUBSCRIBE, "/topic/robot/telemetry", null));
        assertPassed(guard, frame(StompCommand.SUBSCRIBE, "/topic/robot/status", null));
        assertPassed(guard, frame(StompCommand.SUBSCRIBE, "/topic/reports", null));
        assertPassed(guard, frame(StompCommand.SUBSCRIBE, "/topic/alerts", null));

        assertRejected(guard, frame(StompCommand.SUBSCRIBE, "/topic/**", null));
        assertRejected(guard, frame(StompCommand.SUBSCRIBE, "/topic/robot/*", null));
        assertRejected(guard, frame(StompCommand.SUBSCRIBE, "/user/queue/errors", null));
        assertRejected(guard, frame(StompCommand.SUBSCRIBE, null, null));
    }

    @Test
    @DisplayName("관제 토큰이 설정되어 있으면 CONNECT에 토큰이 있어야 한다")
    void connectNeedsToken() {
        StompInboundGuard guard = guardWithToken();

        assertRejected(guard, frame(StompCommand.CONNECT, null, null));
        assertRejected(guard, frame(StompCommand.CONNECT, null, "Bearer wrong"));
        assertPassed(guard, frame(StompCommand.CONNECT, null, "Bearer " + OPERATOR));
    }

    @Test
    @DisplayName("인증된 접속만 구독할 수 있다")
    void subscribeNeedsAuthenticatedSession() {
        StompInboundGuard guard = guardWithToken();

        // CONNECT를 건너뛰고 바로 구독하려는 경우
        assertRejected(guard, frame(StompCommand.SUBSCRIBE, "/topic/reports", null));

        assertPassed(guard, frame(StompCommand.CONNECT, null, "Bearer " + OPERATOR));
        assertPassed(guard, frame(StompCommand.SUBSCRIBE, "/topic/reports", null));
        // 인증했어도 SEND와 다른 주소 구독은 안 된다.
        assertRejected(guard, frame(StompCommand.SEND, "/topic/reports", null));
        assertRejected(guard, frame(StompCommand.SUBSCRIBE, "/topic/other", null));
    }

    @Test
    @DisplayName("연결 유지 신호와 접속 종료는 그대로 통과한다")
    void heartbeatAndDisconnectPass() {
        StompInboundGuard guard = guardWithToken();
        Message<byte[]> heartbeat = MessageBuilder.createMessage(
                new byte[0], StompHeaderAccessor.createForHeartbeat().getMessageHeaders()
        );

        assertPassed(guard, heartbeat);
        assertPassed(guard, frame(StompCommand.DISCONNECT, null, null));
    }

    private StompInboundGuard guardWithoutToken() {
        return new StompInboundGuard(new ApiTokens("", ""));
    }

    private StompInboundGuard guardWithToken() {
        return new StompInboundGuard(new ApiTokens("", OPERATOR));
    }

    private Message<byte[]> frame(StompCommand command, String destination, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setSessionAttributes(session);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void assertPassed(StompInboundGuard guard, Message<?> message) {
        assertSame(message, guard.preSend(message, channel));
    }

    private void assertRejected(StompInboundGuard guard, Message<?> message) {
        assertThrows(MessageDeliveryException.class, () -> guard.preSend(message, channel));
    }
}
