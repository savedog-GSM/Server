package com.example.savedog.Robot.websocket;

import com.example.savedog.Robot.DTO.RobotCommandMessage;
import com.example.savedog.Robot.domain.CommandType;
import com.example.savedog.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Optional;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RobotCommandSenderTest {
    private final RobotSessionRegistry registry = mock(RobotSessionRegistry.class);
    private final WebSocketSession session = mock(WebSocketSession.class);
    private final RobotCommandSender sender = new RobotCommandSender(registry, JsonMapper.builder().build());
    private final RobotCommandMessage stop = new RobotCommandMessage(1L, CommandType.STOP, null, null);

    @Test
    @DisplayName("연결된 로보독에 명령을 JSON으로 보낸다")
    void sendsCommandAsJson() throws IOException {
        when(registry.find()).thenReturn(Optional.of(session));

        sender.send(stop);

        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(sent.capture());
        assertTrue(sent.getValue().getPayload().contains("\"type\":\"STOP\""), sent.getValue().getPayload());
        verify(registry, never()).discard(any(), any());
    }

    @Test
    @DisplayName("연결이 없으면 ROBOT_NOT_CONNECTED")
    void failsWhenNotConnected() {
        when(registry.find()).thenReturn(Optional.empty());

        assertError(ErrorCode.ROBOT_NOT_CONNECTED, () -> sender.send(stop));
    }

    @Test
    @DisplayName("보내다 실패하면 죽은 연결을 정리하고 ROBOT_NOT_CONNECTED")
    void discardsBrokenConnection() throws IOException {
        when(registry.find()).thenReturn(Optional.of(session));
        doThrow(new IOException("Broken pipe")).when(session).sendMessage(any());

        assertError(ErrorCode.ROBOT_NOT_CONNECTED, () -> sender.send(stop));

        verify(registry).discard(session, CloseStatus.SESSION_NOT_RELIABLE);
    }

    @Test
    @DisplayName("밀린 메시지가 한도를 넘는 등 다른 전송 오류도 같은 방식으로 처리한다")
    void discardsOnAnySendFailure() throws IOException {
        when(registry.find()).thenReturn(Optional.of(session));
        doThrow(new IllegalStateException("buffer limit exceeded")).when(session).sendMessage(any());

        assertError(ErrorCode.ROBOT_NOT_CONNECTED, () -> sender.send(stop));

        verify(registry).discard(session, CloseStatus.SESSION_NOT_RELIABLE);
    }
}
