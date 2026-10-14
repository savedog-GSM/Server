package com.example.savedog.global.security;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 컨트롤러 메서드가 실행되기 직전에 토큰을 검사한다.
 * 주소 문자열이 아니라 "실제로 실행될 메서드"를 보고 판단하므로,
 * 주소를 인코딩하거나 변형해서 검사를 피해 가는 방법이 통하지 않는다.
 * 토큰이 설정되지 않은 쪽은 그대로 통과시킨다.
 */
@Component
@RequiredArgsConstructor
public class ApiTokenInterceptor implements HandlerInterceptor {
    private final ApiTokens apiTokens;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 컨트롤러 메서드가 아닌 것(브라우저의 CORS 사전 요청, 없는 주소 등)은 검사 대상이 아니다.
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        boolean allowed = method.hasMethodAnnotation(DeviceEndpoint.class)
                ? apiTokens.matchesDevice(request.getHeader(ApiTokens.DEVICE_HEADER))
                : apiTokens.matchesOperator(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (!allowed) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return true;
    }
}
