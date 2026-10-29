package com.example.savedog.global.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 로보독·아두이노가 부르는 API에 붙인다. 이 표시가 있으면 장치 토큰을, 없으면 관제 토큰을 검사한다. (토큰이 필요 없는 곳은 PublicEndpoint)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DeviceEndpoint {
}
