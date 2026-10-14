package com.example.savedog.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

// 로보독 명령 채널에 접속할 때 장치 토큰을 확인한다. 아무나 로보독인 척 접속해 명령을 가로채지 못하게 한다.
@Component
@RequiredArgsConstructor
public class RobotHandshakeInterceptor implements HandshakeInterceptor {
    private final ApiTokens apiTokens;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String, Object> attributes
    ) {
        if (apiTokens.matchesDevice(request.getHeaders().getFirst(ApiTokens.DEVICE_HEADER))) {
            return true;
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception
    ) {
    }
}
