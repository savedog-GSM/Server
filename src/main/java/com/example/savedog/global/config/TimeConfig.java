package com.example.savedog.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// "지금으로부터 몇 초 이내" 같은 판단에 쓰는 시계. 테스트에서는 고정 시계로 바꿔 끼운다.
@Configuration
public class TimeConfig {
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
