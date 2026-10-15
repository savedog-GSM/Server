package com.example.savedog.global.storage;

import java.net.URI;

// 이미지를 내려받을 수 있는 임시 주소(presigned URL). 화면은 이 값을 img.src에 넣는다.
public record ImageUrlResponse(String url) {
    public static ImageUrlResponse from(URI url) {
        return new ImageUrlResponse(url.toString());
    }
}
