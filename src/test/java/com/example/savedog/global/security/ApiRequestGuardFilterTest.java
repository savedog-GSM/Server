package com.example.savedog.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRequestGuardFilterTest {
    private static final int KB = 1024;

    private final ApiRequestGuardFilter filter = new ApiRequestGuardFilter(
            new ErrorResponseWriter(JsonMapper.builder().build())
    );
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicInteger bytesReadByController = new AtomicInteger(-1);
    // 컨트롤러가 하듯 본문을 끝까지 읽는 체인
    private final FilterChain readsWholeBody = (request, ignored) ->
            bytesReadByController.set(((HttpServletRequest) request).getInputStream().readAllBytes().length);

    @Test
    @DisplayName("한도 안의 본문은 그대로 통과하고 보안 헤더가 붙는다")
    void smallBodyPasses() throws Exception {
        filter.doFilter(post("/api/robot/telemetry", 1 * KB), response, readsWholeBody);

        assertEquals(1 * KB, bytesReadByController.get());
        assertEquals(200, response.getStatus());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test
    @DisplayName("Content-Length가 한도를 넘으면 본문을 읽지 않고 413으로 거절한다")
    void rejectsByContentLength() throws Exception {
        filter.doFilter(post("/api/robot/telemetry", 65 * KB), response, readsWholeBody);

        assertEquals(413, response.getStatus());
        assertEquals(-1, bytesReadByController.get());
        assertTrue(response.getContentAsString().contains("\"code\":\"PAYLOAD_TOO_LARGE\""));
    }

    @Test
    @DisplayName("길이를 알리지 않고 보낸 큰 본문은 읽는 도중에 끊는다")
    void rejectsWhileReadingWhenLengthIsUnknown() {
        MockHttpServletRequest chunked = new MockHttpServletRequest("POST", "/api/robot/telemetry") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        chunked.setContent(new byte[65 * KB]);

        assertThrows(PayloadTooLargeException.class, () -> filter.doFilter(chunked, response, readsWholeBody));
        assertEquals(-1, bytesReadByController.get());
    }

    @Test
    @DisplayName("프레임 업로드 주소만 2MB까지 받는다")
    void frameUploadHasLargerLimit() throws Exception {
        filter.doFilter(post("/api/robot/frames", 500 * KB), response, readsWholeBody);
        assertEquals(500 * KB, bytesReadByController.get());

        MockHttpServletResponse tooLarge = new MockHttpServletResponse();
        filter.doFilter(post("/api/robot/frames", 2 * KB * KB + 1), tooLarge, readsWholeBody);
        assertEquals(413, tooLarge.getStatus());
    }

    @Test
    @DisplayName("위험 신고 업로드 주소는 사진 2MB에 항목 몫까지 받는다")
    void hazardUploadHasLargerLimit() throws Exception {
        filter.doFilter(post("/api/robot/hazards", 2 * KB * KB + 10 * KB), response, readsWholeBody);
        assertEquals(2 * KB * KB + 10 * KB, bytesReadByController.get());

        MockHttpServletResponse tooLarge = new MockHttpServletResponse();
        filter.doFilter(post("/api/robot/hazards", 2 * KB * KB + 64 * KB + 1), tooLarge, readsWholeBody);
        assertEquals(413, tooLarge.getStatus());

        MockHttpServletResponse disguised = new MockHttpServletResponse();
        filter.doFilter(post("/api/robot/hazards/", 500 * KB), disguised, readsWholeBody);
        assertEquals(413, disguised.getStatus());
    }

    @Test
    @DisplayName("프레임 주소를 변형해도 큰 한도를 받지 못한다")
    void disguisedFramePathGetsSmallLimit() throws IOException, jakarta.servlet.ServletException {
        for (String path : new String[]{"/api/robot/frames/", "/api/robot/%66rames", "/api/robot/frames;x=1", "/api/robot/frames/latest"}) {
            MockHttpServletResponse rejected = new MockHttpServletResponse();
            filter.doFilter(post(path, 500 * KB), rejected, readsWholeBody);
            assertEquals(413, rejected.getStatus(), path);
        }
        assertEquals(-1, bytesReadByController.get());
    }

    private MockHttpServletRequest post(String path, int bodyBytes) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContent(new byte[bodyBytes]);
        return request;
    }
}
