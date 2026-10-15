package com.example.savedog.global.config;

import com.example.savedog.global.storage.ImageStorage;
import com.example.savedog.global.storage.S3ImageStorage;
import com.example.savedog.global.storage.UnconfiguredImageStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class StorageConfig {

    @Bean
    public ImageStorage imageStorage(
            @Value("${savedog.s3.bucket:}") String bucket,
            @Value("${savedog.s3.region:ap-northeast-2}") String region
    ) {
        if (bucket.isBlank()) {
            log.warn("S3 버킷이 설정되지 않아 이미지 기능(카메라 프레임, 신고 사진)은 꺼진 상태로 시작합니다.");
            return new UnconfiguredImageStorage();
        }
        return new S3ImageStorage(bucket, region);
    }
}
