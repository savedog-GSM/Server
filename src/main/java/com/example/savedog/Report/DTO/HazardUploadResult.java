package com.example.savedog.Report.DTO;

// created가 false면 근처에 같은 종류의 최근 신고가 있어 새로 만들지 않고 그 신고를 돌려준 것이다.
public record HazardUploadResult(
        ReportResponse report,
        boolean created
) {
}
