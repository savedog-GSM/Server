package com.example.savedog.Report.DTO;

import com.example.savedog.Report.domain.HazardReport;
import com.example.savedog.Report.domain.ReportStatus;
import com.example.savedog.Safety.DTO.RiskResponse;
import com.example.savedog.Safety.domain.HazardType;

import java.time.LocalDateTime;

// hasPhoto가 true면 GET /api/reports/{id}/photo 로 사진을 받을 수 있다.
public record ReportResponse(
        Long id,
        HazardType hazardType,
        double latitude,
        double longitude,
        String address,
        String description,
        RiskResponse risk,
        boolean hasPhoto,
        ReportStatus status,
        String receiptNumber,
        LocalDateTime createdAt,
        LocalDateTime submittedAt
) {
    public static ReportResponse from(HazardReport report) {
        return new ReportResponse(
                report.getId(),
                report.getHazardType(),
                report.getLatitude(),
                report.getLongitude(),
                report.getAddress(),
                report.getDescription(),
                new RiskResponse(report.getRiskLevel(), report.getRiskScore(), report.getRiskReason()),
                report.hasPhoto(),
                report.getStatus(),
                report.getReceiptNumber(),
                report.getCreatedAt(),
                report.getSubmittedAt()
        );
    }
}
