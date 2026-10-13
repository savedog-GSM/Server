package com.example.savedog.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
    FRAME_NOT_FOUND(HttpStatus.NOT_FOUND, "수신된 카메라 프레임이 없습니다."),
    PATH_NOT_FOUND(HttpStatus.NOT_FOUND, "경로를 찾지 못했습니다."),
    ROBOT_NOT_CONNECTED(HttpStatus.CONFLICT, "로보독이 연결되어 있지 않습니다."),
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "아직 구현되지 않은 기능입니다.");

    private final HttpStatus status;
    private final String message;
}
