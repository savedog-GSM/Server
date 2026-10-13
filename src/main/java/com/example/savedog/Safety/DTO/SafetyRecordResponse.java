package com.example.savedog.Safety.DTO;

import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;

import java.time.LocalDateTime;

public record SafetyRecordResponse(
        Long id,
        Long robotId,
        double latitude,
        double longitude,
        SafetyLevel level,
        int score,
        String reason,
        LocalDateTime assessedAt
) {
    public static SafetyRecordResponse from(SafetyRecord record) {
        return new SafetyRecordResponse(
                record.getId(),
                record.getRobotId(),
                record.getLatitude(),
                record.getLongitude(),
                record.getLevel(),
                record.getScore(),
                record.getReason(),
                record.getAssessedAt()
        );
    }
}
