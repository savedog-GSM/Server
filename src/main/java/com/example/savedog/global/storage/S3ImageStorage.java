package com.example.savedog.global.storage;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

// 비공개 S3 버킷에 보관한다. 자격 증명은 AWS 기본 방식(환경 변수, 프로필 등)으로 읽는다.
@Slf4j
public class S3ImageStorage implements ImageStorage, AutoCloseable {
    // 한 번 시도에 5초, 재시도를 합쳐 10초까지만 기다린다.
    private static final Duration ATTEMPT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(10);
    private static final int FORBIDDEN = 403;

    private final String bucket;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final AtomicBoolean headForbiddenWarned = new AtomicBoolean();

    public S3ImageStorage(String bucket, String region) {
        this(bucket, region, "", "", "");
    }

    public S3ImageStorage(String bucket, String region, String accessKey, String secretKey, String sessionToken) {
        this(bucket, region, accessKey, secretKey, sessionToken, null);
    }

    // endpoint: 테스트에서 가짜 S3 서버로 보내기 위한 것. null이면 실제 AWS
    S3ImageStorage(String bucket, String region, String accessKey, String secretKey, String sessionToken, URI endpoint) {
        this.bucket = bucket;
        AwsCredentialsProvider credentialsProvider = credentialsProvider(accessKey, secretKey, sessionToken);
        // 시간 제한이 없으면 S3가 느리거나 끊겼을 때 요청 스레드가 붙잡힌 채 쌓인다.
        var s3Builder = S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(config -> config
                        .apiCallAttemptTimeout(ATTEMPT_TIMEOUT)
                        .apiCallTimeout(CALL_TIMEOUT));
        var presignerBuilder = S3Presigner.builder().region(Region.of(region));
        if (endpoint != null) {
            S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();
            s3Builder.endpointOverride(endpoint).serviceConfiguration(pathStyle);
            presignerBuilder.endpointOverride(endpoint).serviceConfiguration(pathStyle);
        }
        // 키가 제공되면 Spring 설정을 통해 전달된 환경 변수 자격 증명을 쓴다.
        // 비어 있으면 SDK 기본 체인(예: EC2/ECS IAM 역할)을 유지한다.
        if (credentialsProvider != null) {
            s3Builder.credentialsProvider(credentialsProvider);
            presignerBuilder.credentialsProvider(credentialsProvider);
        }
        this.s3 = s3Builder.build();
        this.presigner = presignerBuilder.build();
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
            } catch (S3Exception e) {
                // 파일이 없을 때 버킷 목록 조회 권한(s3:ListBucket)이 없으면 S3는 404 대신 403을 준다.
                // (아직 프레임을 한 장도 안 올렸는데 화면이 최신 프레임을 물어보는 경우가 대표적이다)
                // 읽기 권한(s3:GetObject) 자체가 없을 때도 403이라 둘을 구분할 수 없으므로 "없음"으로 보고 한 번만 알린다.
                if (e.statusCode() == FORBIDDEN) {
                    warnHeadForbiddenOnce(key);
                    return false;
                }
                throw e;
            }
        });
    }

    private void warnHeadForbiddenOnce(String key) {
        if (headForbiddenWarned.compareAndSet(false, true)) {
            log.warn("S3가 {} 확인에 403을 줬습니다. 파일이 아직 없거나 IAM 권한이 부족합니다. "
                    + "없는 파일을 404로 받으려면 버킷에 s3:ListBucket, 사진을 보여 주려면 s3:GetObject 권한이 필요합니다. "
                    + "(이후 같은 경고는 생략)", key);
        }
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

    private static AwsCredentialsProvider credentialsProvider(String accessKey, String secretKey, String sessionToken) {
        String access = accessKey == null ? "" : accessKey.trim();
        String secret = secretKey == null ? "" : secretKey.trim();
        String token = sessionToken == null ? "" : sessionToken.trim();
        if (access.isEmpty() && secret.isEmpty() && token.isEmpty()) {
            return null;
        }
        if (access.isEmpty() || secret.isEmpty()) {
            throw new IllegalStateException("AWS_ACCESS_KEY_ID와 AWS_SECRET_ACCESS_KEY를 함께 설정해야 합니다.");
        }
        if (!token.isEmpty()) {
            return StaticCredentialsProvider.create(AwsSessionCredentials.create(access, secret, token));
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret));
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
