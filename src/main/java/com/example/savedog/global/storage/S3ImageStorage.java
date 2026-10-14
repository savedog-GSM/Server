package com.example.savedog.global.storage;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.function.Supplier;

// 비공개 S3 버킷에 보관한다. 자격 증명은 AWS 기본 방식(환경 변수, 프로필 등)으로 읽는다.
@Slf4j
public class S3ImageStorage implements ImageStorage, AutoCloseable {
    // 한 번 시도에 5초, 재시도를 합쳐 10초까지만 기다린다.
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);

    private final String bucket;
    private final S3Client s3;
    private final S3Presigner presigner;

    public S3ImageStorage(String bucket, String region) {
        this.bucket = bucket;
        // 시간 제한이 없으면 S3가 느리거나 끊겼을 때 요청 스레드가 붙잡힌 채 쌓인다.
        this.s3 = S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(config -> config
                        .apiCallAttemptTimeout(ATTEMPT_TIMEOUT)
                        .apiCallTimeout(CALL_TIMEOUT))
                .build();
        this.presigner = S3Presigner.builder().region(Region.of(region)).build();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        call("올리기", key, () -> s3.putObject(
                request -> request.bucket(bucket).key(key).contentType(contentType),
                RequestBody.fromBytes(content)
        ));
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        call("복사", sourceKey, () -> s3.copyObject(request -> request
                .sourceBucket(bucket).sourceKey(sourceKey)
                .destinationBucket(bucket).destinationKey(targetKey)));
    }

    @Override
    public boolean exists(String key) {
        return call("확인", key, () -> {
            try {
                s3.headObject(request -> request.bucket(bucket).key(key));
                return true;
            } catch (NoSuchKeyException e) {
                return false;
            }
        });
    }

    @Override
    public URI presignedUrl(String key, Duration validFor) {
        return call("주소 발급", key, () -> {
            try {
                return presigner.presignGetObject(presign -> presign
                        .signatureDuration(validFor)
                        .getObjectRequest(request -> request.bucket(bucket).key(key))
                ).url().toURI();
            } catch (URISyntaxException e) {
                throw new IllegalStateException("S3가 준 주소를 해석할 수 없습니다.", e);
            }
        });
    }

    @Override
    public void close() {
        presigner.close();
        s3.close();
    }

    // AWS 쪽 오류(자격 증명 없음, 버킷 없음, 네트워크 등)를 한 가지 오류로 바꾼다.
    private <T> T call(String action, String key, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (SdkException | IllegalStateException e) {
            log.warn("S3 {} 실패 (key={})", action, key, e);
            throw new BusinessException(
                    ErrorCode.IMAGE_STORAGE_UNAVAILABLE,
                    "이미지 저장소(S3)에 접근하지 못했습니다. 버킷과 자격 증명 설정을 확인해 주세요.",
                    e
            );
        }
    }
}
