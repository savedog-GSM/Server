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
    private static final String DEFAULT_REGION = "ap-northeast-2";

    @Bean
    public ImageStorage imageStorage(
            @Value("${savedog.s3.bucket:}") String bucket,
            @Value("${savedog.s3.region:" + DEFAULT_REGION + "}") String region
    ) {
        if (bucket.isBlank()) {
            log.warn("S3 버킷이 설정되지 않아 이미지 기능(카메라 프레임, 신고 사진)은 꺼진 상태로 시작합니다.");
            return new UnconfiguredImageStorage();
        }
        // AWS_REGION=처럼 값 없이 적어 두면 S3 클라이언트를 만들다 서버가 죽으므로 기본 리전을 쓴다.
        if (region.isBlank()) {
            log.warn("AWS_REGION이 비어 있어 기본 리전({})을 씁니다.", DEFAULT_REGION);
            region = DEFAULT_REGION;
        }
        return new S3ImageStorage(bucket.trim(), region.trim());
    }
}
