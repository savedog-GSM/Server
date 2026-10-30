package com.example.savedog.global.storage;

import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import com.example.savedog.global.security.PublicEndpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 서버 디스크에 보관한 이미지를 내준다. 토큰 대신 주소의 서명으로 확인한다. (LocalImageStorage)
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class LocalImageController {
    private final ImageStorage imageStorage;

    @PublicEndpoint
    @GetMapping
    public ResponseEntity<byte[]> read(
            @RequestParam String key,
            @RequestParam long expires,
            @RequestParam String signature
    ) {
        if (!(imageStorage instanceof LocalImageStorage local)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore())
                .body(local.read(key, expires, signature));
    }
}
