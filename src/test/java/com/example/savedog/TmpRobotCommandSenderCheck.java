package com.example.savedog;

import com.example.savedog.Robot.DTO.RobotCommandMessage;
import com.example.savedog.Robot.domain.CommandType;
import com.example.savedog.Robot.websocket.RobotCommandSender;
import com.example.savedog.Robot.websocket.RobotSessionRegistry;
import com.example.savedog.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TmpRobotCommandSenderCheck {
    @Value("${local.server.port}")
    int port;
    @Autowired
    RobotCommandSender sender;
    @Autowired
    RobotSessionRegistry registry;

    @Test
    void sendsCommandJsonToConnectedRobot() throws Exception {
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocketSession session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage m) {
                received.add(m.getPayload());
            }
        }, "ws://localhost:" + port + "/ws/robot?robotId=9").get(5, TimeUnit.SECONDS);
        for (int i = 0; i < 50 && !registry.isConnected(9L); i++) {
            Thread.sleep(50);
        }

        sender.send(9L, new RobotCommandMessage(42, CommandType.FORWARD, 60, null));

        assertEquals("{\"commandId\":42,\"type\":\"FORWARD\",\"speed\":60,\"durationMs\":null}",
                received.poll(5, TimeUnit.SECONDS));
        assertThrows(BusinessException.class,
                () -> sender.send(999L, new RobotCommandMessage(43, CommandType.STOP, null, null)));
        session.close();
    }
}
