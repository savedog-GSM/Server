package com.example.savedog.global.storage;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;

/**
 * S3 대신 서버 디스크에 이미지를 보관한다. (S3 권한을 쓸 수 없을 때)
 * img 태그는 인증 헤더를 못 붙이므로 S3 presigned URL처럼 주소에 만료 시각과 서명을 넣어 내준다.
 * 서명 키는 서버를 켤 때마다 새로 만든다. 주소는 몇 분만 쓰는 것이라 다시 켜면 새로 받으면 된다.
 */
public class LocalImageStorage implements ImageStorage {
    private static final String SIGNING_ALGORITHM = "HmacSHA256";

    private final Path root;
    private final String publicBaseUrl;
    private final Clock clock;
    private final byte[] signingKey = new byte[32];

    public LocalImageStorage(Path root, String publicBaseUrl, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.publicBaseUrl = publicBaseUrl.trim().replaceAll("/+$", "");
        this.clock = clock;
        new SecureRandom().nextBytes(signingKey);
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Path target = pathOf(key);
        try {
            Files.createDirectories(target.getParent());
            // 다 쓴 뒤에 바꿔 넣는다. 그래야 화면이 쓰다 만 사진을 읽지 않는다.
            Path temp = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            try {
                Files.write(temp, content);
                move(temp, target);
            } finally {
                Files.deleteIfExists(temp);
            }
        } catch (IOException e) {
            throw unavailable("저장", e);
        }
    }

    @Override
    public void copy(String sourceKey, String targetKey) {
        Path target = pathOf(targetKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(pathOf(sourceKey), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw unavailable("복사", e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(pathOf(key));
    }

    @Override
    public URI presignedUrl(String key, Duration validFor) {
        pathOf(key);
        if (publicBaseUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE,
                    "PUBLIC_BASE_URL이 설정되지 않아 이미지 주소를 만들 수 없습니다.");
        }
        long expires = clock.millis() + validFor.toMillis();
        return URI.create("%s/api/images?key=%s&expires=%d&signature=%s".formatted(
                publicBaseUrl, URLEncoder.encode(key, StandardCharsets.UTF_8), expires, sign(key, expires)));
    }

    // 서명한 주소로 들어온 요청의 이미지를 읽는다. 서명이 틀리거나 만료되면 401, 파일이 없으면 404
    public byte[] read(String key, long expires, String signature) {
        boolean valid = MessageDigest.isEqual(
                sign(key, expires).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
        if (!valid || clock.millis() > expires) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Path path = pathOf(key);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.FRAME_NOT_FOUND, "이미지를 찾을 수 없습니다.");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw unavailable("읽기", e);
        }
    }

    // key는 서버 코드가 만들지만, 보관 폴더 밖을 가리키는 값은 어떤 경우에도 막는다.
    private Path pathOf(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "이미지 경로가 올바르지 않습니다.");
        }
        return path;
    }

    private static void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String sign(String key, long expires) {
        try {
            Mac mac = Mac.getInstance(SIGNING_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, SIGNING_ALGORITHM));
            byte[] message = (expires + "\n" + key).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("이미지 주소 서명 실패", e);
        }
    }

    private BusinessException unavailable(String action, IOException e) {
        return new BusinessException(ErrorCode.IMAGE_STORAGE_UNAVAILABLE,
                "이미지 %s에 실패했습니다. 서버의 저장 폴더를 확인해 주세요.".formatted(action), e);
    }
}
