package com.example.savedog.global.security;

import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// 컨트롤러에 닿기 전(필터)에서 거절할 때도 다른 오류와 같은 { code, message } 형태로 응답하기 위한 도구
@Component
@RequiredArgsConstructor
public class ErrorResponseWriter {
    private final JsonMapper jsonMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.from(errorCode)));
    }
}
