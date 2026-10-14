package com.example.savedog.global.security;

import java.io.IOException;

// 요청 본문을 읽다가 허용 크기를 넘었을 때
public class PayloadTooLargeException extends IOException {
    public PayloadTooLargeException(long limitBytes) {
        super("요청 본문이 " + limitBytes + "바이트를 넘었습니다.");
    }
}
