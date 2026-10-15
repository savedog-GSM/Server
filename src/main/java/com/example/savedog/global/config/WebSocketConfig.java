package com.example.savedog.global.config;

import com.example.savedog.global.security.AllowedOrigins;
import com.example.savedog.global.security.StompInboundGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    // 화면이 서버로 보내는 것은 CONNECT·SUBSCRIBE 같은 짧은 프레임뿐이다.
    private static final int MAX_INBOUND_MESSAGE_BYTES = 8 * 1024;
    private static final int SEND_BUFFER_BYTES = 512 * 1024;
    private static final int SEND_TIME_LIMIT_MS = 10_000;

    private final AllowedOrigins allowedOrigins;
    private final StompInboundGuard stompInboundGuard;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    // 허용한 출처의 화면만 접속할 수 있다. 아무 웹사이트나 붙어서 위치·센서 값을 엿보지 못하게 한다.
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.values());
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompInboundGuard);
    }

    // 받는 속도가 느린 화면 하나 때문에 서버 메모리가 계속 쌓이지 않게 한도를 둔다.
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(MAX_INBOUND_MESSAGE_BYTES)
                .setSendBufferSizeLimit(SEND_BUFFER_BYTES)
                .setSendTimeLimit(SEND_TIME_LIMIT_MS);
    }
}
