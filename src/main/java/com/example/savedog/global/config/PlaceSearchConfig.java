package com.example.savedog.global.config;

import com.example.savedog.Place.service.KakaoPlaceSearchClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class PlaceSearchConfig {
    @Bean
    public KakaoPlaceSearchClient kakaoPlaceSearchClient(
            @Value("${savedog.kakao.rest-api-key:}") String restApiKey,
            JsonMapper jsonMapper
    ) {
        return new KakaoPlaceSearchClient(restApiKey, jsonMapper);
    }
}
