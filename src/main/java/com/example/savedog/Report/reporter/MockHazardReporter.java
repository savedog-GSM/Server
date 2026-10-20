package com.example.savedog.Report.reporter;

import com.example.savedog.Report.domain.HazardReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 시연용 모의 민원 창구. 실제로 어디에도 보내지 않고 MOCK-으로 시작하는 접수번호만 만들어 준다.
 * REPORT_MOCK_AUTHORITY=true일 때만 쓴다. 접수번호만 봐도 모의 접수인 것을 알 수 있게 접두어를 붙인다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "savedog.report.mock-authority", havingValue = "true")
@RequiredArgsConstructor
public class MockHazardReporter implements HazardReporter {
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final Clock clock;

    @Override
    public String submit(HazardReport report) {
        String receiptNumber = "MOCK-%s-%06d".formatted(LocalDate.now(clock).format(DAY), report.getId());
        log.info("모의 민원 창구: 신고 {}를 접수번호 {}로 처리했습니다. (실제로 보내지 않음)", report.getId(), receiptNumber);
        return receiptNumber;
    }
}
