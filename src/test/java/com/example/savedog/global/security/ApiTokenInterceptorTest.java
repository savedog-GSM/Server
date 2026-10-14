package com.example.savedog.global.security;

import com.example.savedog.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static com.example.savedog.support.Errors.assertError;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTokenInterceptorTest {
    private static final String DEVICE = "device-token-0123456789";
    private static final String OPERATOR = "operator-token-0123456789";

    private final ApiTokenInterceptor interceptor = new ApiTokenInterceptor(new ApiTokens(DEVICE, OPERATOR));
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("@DeviceEndpoint 메서드는 장치 토큰이 있어야 실행된다")
    void deviceEndpointNeedsDeviceToken() throws Exception {
        HandlerMethod handler = handler("fromDevice");

        assertError(ErrorCode.UNAUTHORIZED, () -> interceptor.preHandle(new MockHttpServletRequest(), response, handler));
        assertError(ErrorCode.UNAUTHORIZED, () -> interceptor.preHandle(withDeviceToken("wrong"), response, handler));
        // 관제 토큰으로는 센서 값을 꾸며 보낼 수 없다.
        assertError(ErrorCode.UNAUTHORIZED, () -> interceptor.preHandle(withOperatorToken(OPERATOR), response, handler));

        assertTrue(interceptor.preHandle(withDeviceToken(DEVICE), response, handler));
    }

    @Test
    @DisplayName("그 밖의 메서드는 관제 토큰이 있어야 실행된다")
    void otherEndpointsNeedOperatorToken() throws Exception {
        HandlerMethod handler = handler("fromOperator");

        assertError(ErrorCode.UNAUTHORIZED, () -> interceptor.preHandle(new MockHttpServletRequest(), response, handler));
        assertError(ErrorCode.UNAUTHORIZED, () -> interceptor.preHandle(withOperatorToken("wrong"), response, handler));
        // 장치 토큰으로는 로보독에 명령을 보낼 수 없다.
        assertError(ErrorCode.UNAUTHORIZED, () -> interceptor.preHandle(withDeviceToken(DEVICE), response, handler));

        assertTrue(interceptor.preHandle(withOperatorToken(OPERATOR), response, handler));
    }

    @Test
    @DisplayName("토큰을 설정하지 않았으면 모두 통과한다")
    void noTokensConfigured() throws Exception {
        ApiTokenInterceptor open = new ApiTokenInterceptor(new ApiTokens("", ""));

        assertTrue(open.preHandle(new MockHttpServletRequest(), response, handler("fromDevice")));
        assertTrue(open.preHandle(new MockHttpServletRequest(), response, handler("fromOperator")));
    }

    @Test
    @DisplayName("컨트롤러 메서드가 아닌 처리기(CORS 사전 요청 등)는 검사하지 않는다")
    void nonControllerHandlersPass() {
        assertTrue(interceptor.preHandle(new MockHttpServletRequest(), response, new Object()));
    }

    private MockHttpServletRequest withDeviceToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiTokens.DEVICE_HEADER, token);
        return request;
    }

    private MockHttpServletRequest withOperatorToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new SampleController(), SampleController.class.getMethod(methodName));
    }

    static class SampleController {
        @DeviceEndpoint
        public void fromDevice() {
        }

        public void fromOperator() {
        }
    }
}
