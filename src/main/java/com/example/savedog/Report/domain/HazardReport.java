package com.example.savedog.Report.domain;

import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
// 중복 신고 확인은 생성 시각으로, 목록은 상태별 최신순으로 읽는다.
@Table(indexes = {
        @Index(name = "idx_hazard_report_created_at", columnList = "created_at"),
        @Index(name = "idx_hazard_report_status_created_at", columnList = "status, created_at")
})
public class HazardReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long safetyRecordId;

    // DB의 ENUM 타입으로 두면 종류를 늘릴 때마다 기존 테이블을 고쳐야 해서 문자열로 저장한다. (HazardTypeColumn)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 32)
    private HazardType hazardType;

    private double latitude;

    private double longitude;

    private String address;

    private String description;

    @Enumerated(EnumType.STRING)
    private SafetyLevel riskLevel;

    private int riskScore;

    private String riskReason;

    // S3에 보관한 신고 사진의 키. 사진이 없으면 null
    private String photoKey;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    private String receiptNumber;

    private LocalDateTime createdAt;

    private LocalDateTime submittedAt;

    // 두 요청이 같은 신고를 동시에 처리하면 늦은 쪽을 실패시킨다.
    @Version
    private Long version;

    public HazardReport(
            Long safetyRecordId,
            HazardType hazardType,
            double latitude,
            double longitude,
            String address,
            String description,
            SafetyLevel riskLevel,
            int riskScore,
            String riskReason
    ) {
        this.safetyRecordId = safetyRecordId;
        this.hazardType = hazardType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.description = description;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.riskReason = riskReason;
        this.status = ReportStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void attachPhoto(String photoKey) {
        this.photoKey = photoKey;
    }

    public boolean hasPhoto() {
        return photoKey != null;
    }

    // 제출·기각은 승인 대기(PENDING)나 제출 실패(FAILED) 상태에서만 할 수 있다.
    public void ensureOpen() {
        if (status != ReportStatus.PENDING && status != ReportStatus.FAILED) {
            throw new BusinessException(ErrorCode.REPORT_ALREADY_PROCESSED);
        }
    }

    public void markSubmitted(String receiptNumber) {
        ensureOpen();
        this.status = ReportStatus.SUBMITTED;
        this.receiptNumber = receiptNumber;
        this.submittedAt = LocalDateTime.now();
    }

    public void markFailed() {
        ensureOpen();
        this.status = ReportStatus.FAILED;
    }

    public void dismiss() {
        ensureOpen();
        this.status = ReportStatus.DISMISSED;
    }
}
