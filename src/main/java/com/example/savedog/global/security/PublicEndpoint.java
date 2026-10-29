package com.example.savedog.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 토큰 없이 누구나 부를 수 있는 API에 붙인다. (스마트폰 위치 전송처럼 토큰을 넣기 번거로운 곳)
// 위조된 값이 들어와도 큰일이 나지 않는 API에만 붙인다.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}
