package com.example.savedog.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 관제 화면이 STOMP로 보내는 프레임을 검사한다. 화면은 구독만 할 수 있다.
 * - SEND 금지: 막지 않으면 접속한 누구나 /topic/** 으로 가짜 센서 값·신고 알림을 뿌릴 수 있다.
 * - 구독은 정해진 주소만 허용한다.
 * - 관제 토큰이 설정되어 있으면 CONNECT 때 토큰을 확인하고, 확인된 접속만 구독할 수 있다.
 * 거절하면 스프링이 ERROR 프레임을 보내고 접속을 끊는다.
 */
@Component
@RequiredArgsConstructor
public class StompInboundGuard implements ChannelInterceptor {
    private static final Set<String> SUBSCRIBABLE = Set.of(
            "/topic/robot/telemetry", "/topic/robot/status", "/topic/robot/events", "/topic/reports", "/topic/alerts"
    );
    private static final String AUTHENTICATED = "savedog.authenticated";

    private final ApiTokens apiTokens;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // 헤더를 읽기만 하므로 감싸서 본다. 어떤 경로로 들어온 메시지든 빠짐없이 검사된다.
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();
        // 명령이 없는 것은 연결 유지용 신호(heartbeat)다.
        if (command == null) {
            return message;
        }
        switch (command) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> checkSubscription(accessor);
            case SEND -> throw new MessageDeliveryException(message, "이 서버는 구독만 허용합니다.");
            default -> {
                // DISCONNECT, UNSUBSCRIBE 등은 그대로 둔다.
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        if (!apiTokens.matchesOperator(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION))) {
            throw new MessageDeliveryException("토큰이 없거나 올바르지 않습니다.");
        }
        Map<String, Object> session = accessor.getSessionAttributes();
        if (session != null) {
            session.put(AUTHENTICATED, Boolean.TRUE);
        }
    }

    private void checkSubscription(StompHeaderAccessor accessor) {
        if (apiTokens.operatorRequired() && !isAuthenticated(accessor)) {
            throw new MessageDeliveryException("CONNECT로 먼저 인증해야 합니다.");
        }
        String destination = accessor.getDestination();
        if (destination == null || !SUBSCRIBABLE.contains(destination)) {
            throw new MessageDeliveryException("구독할 수 없는 주소입니다.");
        }
    }

    private boolean isAuthenticated(StompHeaderAccessor accessor) {
        Map<String, Object> session = accessor.getSessionAttributes();
        return session != null && Boolean.TRUE.equals(session.get(AUTHENTICATED));
    }
}
