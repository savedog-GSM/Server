package com.example.savedog.global.exception;

import com.example.savedog.global.security.PayloadTooLargeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * 모든 오류를 { code, message } 한 가지 형태로 내려준다.
 * 스프링이 직접 던지는 오류(없는 주소, 잘못된 메서드·Content-Type 등)는 부모 클래스가 잡아
 * handleExceptionInternal로 모아 주므로 거기서 같은 형태로 바꾼다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus().is5xxServerError()) {
            log.warn("{}: {}", errorCode, e.getMessage(), e.getCause());
        }
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, e.getMessage()));
    }

    // 같은 신고를 두 요청이 동시에 처리하려다 한쪽이 밀린 경우
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(ObjectOptimisticLockingFailureException e) {
        ErrorCode errorCode = ErrorCode.REPORT_ALREADY_PROCESSED;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    // 위에서 잡지 못한 나머지. 내부 사정은 응답에 싣지 않고 로그에만 남긴다.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        // 응답을 보내는 도중 상대가 연결을 끊은 경우. 서버 잘못이 아니고 응답을 받을 쪽도 없다.
        if (DisconnectedClientHelper.isClientDisconnectedException(e)) {
            log.debug("클라이언트가 응답을 받기 전에 연결을 끊었습니다: {}", e.toString());
            return null;
        }
        log.error("처리하지 못한 오류", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        // 본문을 읽다가 크기 제한에 걸린 경우(Content-Length 없이 보내면 읽는 도중에야 알 수 있다)
        if (causedBy(ex, PayloadTooLargeException.class)) {
            ErrorCode errorCode = ErrorCode.PAYLOAD_TOO_LARGE;
            return new ResponseEntity<>(ErrorResponse.from(errorCode), headers, errorCode.getStatus());
        }
        return invalidRequest("요청 본문을 읽을 수 없습니다. JSON 형식과 값의 종류를 확인해 주세요.", headers);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        return invalidRequest("필수 파라미터 " + ex.getParameterName() + " 값이 없습니다.", headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request
    ) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ex.getPropertyName();
        return invalidRequest(name + " 값의 형식이 올바르지 않습니다.", headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request
    ) {
        if (statusCode.is5xxServerError()) {
            log.error("처리하지 못한 오류", ex);
        }
        return new ResponseEntity<>(ErrorResponse.from(errorCodeFor(statusCode)), headers, statusCode);
    }

    private ResponseEntity<Object> invalidRequest(String message, HttpHeaders headers) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return new ResponseEntity<>(ErrorResponse.of(errorCode, message), headers, errorCode.getStatus());
    }

    private boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        // 원인이 자기 자신을 가리키는 예외가 있어도 끝없이 돌지 않게 깊이를 제한한다.
        Throwable current = error;
        for (int depth = 0; current != null && depth < 10; depth++) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ErrorCode errorCodeFor(HttpStatusCode statusCode) {
        if (statusCode.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        return switch (statusCode.value()) {
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ErrorCode.INVALID_REQUEST;
        };
    }
}
