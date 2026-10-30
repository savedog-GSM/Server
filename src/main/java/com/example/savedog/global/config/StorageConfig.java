package com.example.savedog.global.config;

import com.example.savedog.global.storage.ImageStorage;
import com.example.savedog.global.storage.LocalImageStorage;
import com.example.savedog.global.storage.S3ImageStorage;
import com.example.savedog.global.storage.UnconfiguredImageStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;

@Slf4j
@Configuration
public class StorageConfig {
    private static final String DEFAULT_REGION = "ap-northeast-2";

    @Bean
    public ImageStorage imageStorage(
            @Value("${savedog.s3.bucket:}") String bucket,
            @Value("${savedog.s3.region:" + DEFAULT_REGION + "}") String region,
            @Value("${savedog.s3.access-key:}") String accessKey,
            @Value("${savedog.s3.secret-key:}") String secretKey,
            @Value("${savedog.s3.session-token:}") String sessionToken,
            @Value("${savedog.storage.local-dir:}") String localDir,
            @Value("${savedog.public-base-url:}") String publicBaseUrl,
            Clock clock
    ) {
        // 저장 폴더를 지정하면 S3 설정이 있어도 서버 디스크에 보관한다.
        if (!localDir.isBlank()) {
            log.info("이미지를 서버 디스크에 보관합니다: {}", localDir.trim());
            return new LocalImageStorage(Path.of(localDir.trim()), publicBaseUrl, clock);
        }
        if (bucket.isBlank()) {
            log.warn("S3 버킷이 설정되지 않아 이미지 기능(카메라 프레임, 신고 사진)은 꺼진 상태로 시작합니다.");
            return new UnconfiguredImageStorage();
        }
        if (accessKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "S3_BUCKET을 설정했다면 AWS_ACCESS_KEY_ID와 AWS_SECRET_ACCESS_KEY도 설정해야 합니다."
            );
        }
        // AWS_REGION=처럼 값 없이 적어 두면 S3 클라이언트를 만들다 서버가 죽으므로 기본 리전을 쓴다.
        if (region.isBlank()) {
            log.warn("AWS_REGION이 비어 있어 기본 리전({})을 씁니다.", DEFAULT_REGION);
            region = DEFAULT_REGION;
        }
        return new S3ImageStorage(bucket.trim(), region.trim(), accessKey, secretKey, sessionToken);
    }
}
