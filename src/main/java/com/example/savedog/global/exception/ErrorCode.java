package com.example.savedog.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 토큰이 없거나 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "요청 본문이 너무 큽니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
    FRAME_NOT_FOUND(HttpStatus.NOT_FOUND, "수신된 카메라 프레임이 없습니다."),
    PATH_NOT_FOUND(HttpStatus.NOT_FOUND, "경로를 찾지 못했습니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "근처에서 장소를 찾지 못했습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다."),
    ROBOT_NOT_CONNECTED(HttpStatus.CONFLICT, "로보독이 연결되어 있지 않습니다."),
    LOCATION_UNAVAILABLE(HttpStatus.CONFLICT, "최근에 받은 스마트폰 위치가 없습니다."),
    REPORT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "이미 처리된 신고입니다."),
    REPORT_SUBMIT_FAILED(HttpStatus.BAD_GATEWAY, "민원 창구 제출에 실패했습니다."),
    KAKAO_API_KEY_MISSING(HttpStatus.SERVICE_UNAVAILABLE, "카카오 REST API 키가 설정되지 않았습니다."),
    KAKAO_PLACE_SEARCH_FAILED(HttpStatus.BAD_GATEWAY, "카카오 장소 검색에 실패했습니다."),
    IMAGE_STORAGE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "이미지 저장소를 사용할 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
