package com.example.savedog.Safety.DTO;

import com.example.savedog.Safety.domain.SafetyRecord;

import java.time.LocalDateTime;

public record SafetyRecordResponse(
        Long id,
        double latitude,
        double longitude,
        RiskResponse risk,
        LocalDateTime assessedAt
) {
    public static SafetyRecordResponse from(SafetyRecord record) {
        return new SafetyRecordResponse(
                record.getId(),
                record.getLatitude(),
                record.getLongitude(),
                new RiskResponse(record.getRiskLevel(), record.getRiskScore(), record.getReason()),
                record.getAssessedAt()
        );
    }
}
