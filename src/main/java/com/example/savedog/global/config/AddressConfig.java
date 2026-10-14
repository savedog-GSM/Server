package com.example.savedog.global.config;

import com.example.savedog.Report.address.AddressResolver;
import com.example.savedog.Report.address.KakaoAddressResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

@Slf4j
@Configuration
public class AddressConfig {

    @Bean
    public AddressResolver addressResolver(
            @Value("${savedog.kakao.rest-api-key:}") String restApiKey,
            JsonMapper jsonMapper
    ) {
        if (restApiKey.isBlank()) {
            log.warn("카카오 REST API 키가 설정되지 않아 신고의 주소는 비워 둡니다.");
            return (latitude, longitude) -> Optional.empty();
        }
        return new KakaoAddressResolver(restApiKey, jsonMapper);
    }
}
