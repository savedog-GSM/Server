package com.example.savedog.global.security;

import com.example.savedog.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 모든 요청에 걸리는 공통 방어.
 * - 본문 크기 제한: 아주 큰 본문을 보내 서버 메모리를 채우는 것을 막는다. 읽기 전에 Content-Length로 거르고,
 *   길이를 알리지 않은(chunked) 요청은 읽는 도중에 한도를 넘으면 끊는다.
 * - 응답 헤더: 위치·센서 기록이 브라우저나 중간 서버에 캐시되지 않게 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class ApiRequestGuardFilter extends OncePerRequestFilter {
    public static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;
    private static final int MAX_JSON_BYTES = 64 * 1024;
    private static final String FRAME_UPLOAD_PATH = "/api/robot/frames";

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        // 큰 한도는 프레임 업로드 주소와 글자 그대로 같을 때만 준다. 변형된 주소는 작은 한도를 받는다.
        long limit = FRAME_UPLOAD_PATH.equals(request.getRequestURI()) ? MAX_FRAME_BYTES : MAX_JSON_BYTES;
        if (request.getContentLengthLong() > limit) {
            errorResponseWriter.write(response, ErrorCode.PAYLOAD_TOO_LARGE);
            return;
        }
        chain.doFilter(new SizeLimitedRequest(request, limit), response);
    }

    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;

        private SizeLimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new SizeLimitedStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class SizeLimitedStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        private SizeLimitedStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                counted(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                counted(read);
            }
            return read;
        }

        private void counted(int bytes) throws PayloadTooLargeException {
            count += bytes;
            if (count > limit) {
                throw new PayloadTooLargeException(limit);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
