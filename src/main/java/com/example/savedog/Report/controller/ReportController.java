package com.example.savedog.Report.controller;

import com.example.savedog.Report.DTO.ReportResponse;
import com.example.savedog.Report.DTO.ReportSubmitRequest;
import com.example.savedog.Report.domain.ReportStatus;
import com.example.savedog.Report.service.ReportService;
import com.example.savedog.global.response.ApiResponse;
import com.example.savedog.global.storage.ImageUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @GetMapping
    public List<ReportResponse> findAll(@RequestParam(required = false) ReportStatus status) {
        return reportService.findAll(status);
    }

    @GetMapping("/{reportId}")
    public ReportResponse find(@PathVariable Long reportId) {
        return reportService.find(reportId);
    }

    // 사진은 S3에 있으므로 presigned URL로 넘긴다.
    @GetMapping("/{reportId}/photo")
    public ResponseEntity<?> getPhoto(@PathVariable Long reportId) {
        try{
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(reportService.getPhotoUrl(reportId))
                .build();
        }catch(IllegalArgumentException e){
            return ResponseEntity.badRequest().body(new ApiResponse(400, "잘못된 요청입니다."));
        }

    }

    // <img>는 인증 헤더를 붙일 수 없어서, 관제 토큰을 쓰는 화면은 주소만 받아 img.src에 넣는다.
    @GetMapping("/{reportId}/photo/url")
    public ImageUrlResponse getPhotoUrl(@PathVariable Long reportId) {
        return ImageUrlResponse.from(reportService.getPhotoUrl(reportId));
    }

    @PostMapping("/{reportId}/submit")
    public ReportResponse submit(
            @PathVariable Long reportId,
            @RequestBody(required = false) ReportSubmitRequest request
    ) {
        return reportService.submit(reportId, request);
    }

    @PostMapping("/{reportId}/dismiss")
    public ReportResponse dismiss(@PathVariable Long reportId) {
        return reportService.dismiss(reportId);
    }
}
