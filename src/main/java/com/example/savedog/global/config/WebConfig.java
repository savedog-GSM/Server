package com.example.savedog.global.config;

import com.example.savedog.global.security.AllowedOrigins;
import com.example.savedog.global.security.ApiTokenInterceptor;
import com.example.savedog.global.security.ApiTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final AllowedOrigins allowedOrigins;
    private final ApiTokenInterceptor apiTokenInterceptor;

    // 프론트엔드가 다른 주소(예: localhost:3000)에서 API를 부를 수 있게 허용한다.
    // 목록에 없는 출처에서 온 브라우저 요청은 스프링이 403으로 거절한다.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.values())
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", ApiTokens.DEVICE_HEADER);
    }

    // 주소 패턴을 따로 주지 않는다. 모든 컨트롤러 메서드가 빠짐없이 검사받게 하기 위함이다.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiTokenInterceptor);
    }
}
