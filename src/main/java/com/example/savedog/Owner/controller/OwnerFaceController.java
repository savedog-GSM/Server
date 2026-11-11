package com.example.savedog.Owner.controller;

import com.example.savedog.Owner.DTO.OwnerFaceDeleteResponse;
import com.example.savedog.Owner.DTO.OwnerFaceResponse;
import com.example.savedog.Owner.service.OwnerFaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 주인 얼굴 등록·삭제 (관제 토큰). 사진은 multipart의 image 항목으로 한 장씩 올린다.
@RestController
@RequestMapping("/api/owner/faces")
@RequiredArgsConstructor
public class OwnerFaceController {
    private final OwnerFaceService ownerFaceService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerFaceResponse register(@RequestPart(value = "image", required = false) MultipartFile image) {
        return ownerFaceService.register(image);
    }

    // ?id=3 이면 그 얼굴만, 없으면 전부 지운다.
    @DeleteMapping
    public OwnerFaceDeleteResponse delete(@RequestParam(required = false) Long id) {
        return ownerFaceService.delete(id);
    }
}
