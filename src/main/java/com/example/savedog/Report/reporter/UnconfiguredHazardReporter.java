package com.example.savedog.Report.reporter;

import com.example.savedog.Report.domain.HazardReport;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 연동할 민원 창구가 아직 정해지지 않았을 때의 기본 구현. 자동 제출은 항상 실패로 처리한다.
// REPORT_MOCK_AUTHORITY=true면 대신 MockHazardReporter를 쓴다.
@Component
@ConditionalOnProperty(name = "savedog.report.mock-authority", havingValue = "false", matchIfMissing = true)
public class UnconfiguredHazardReporter implements HazardReporter {

    @Override
    public String submit(HazardReport report) {
        throw new BusinessException(
                ErrorCode.REPORT_SUBMIT_FAILED,
                "연동된 민원 창구가 없어 자동으로 제출할 수 없습니다. 창구에 직접 접수한 뒤 접수번호(receiptNumber)를 넣어 다시 제출해 주세요."
        );
    }
}
