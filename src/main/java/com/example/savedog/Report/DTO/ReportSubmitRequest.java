package com.example.savedog.Report.DTO;

// receiptNumber: 관제자가 창구에 직접 접수한 경우의 접수번호. 주면 서버는 창구로 보내지 않고 번호만 기록한다.
public record ReportSubmitRequest(
        String receiptNumber
) {
}
