package com.example.savedog.Report.reporter;

import com.example.savedog.Report.domain.HazardReport;
import com.example.savedog.Safety.domain.HazardType;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockHazardReporterTest {
    @Test
    @DisplayName("모의 창구는 MOCK-날짜-신고번호 형식의 접수번호를 준다")
    void issuesMockReceiptNumber() {
        HazardReport report = new HazardReport(null, HazardType.FIRE, 37.5, 127.0, null, "화재", SafetyLevel.DANGER, 5, null);
        ReflectionTestUtils.setField(report, "id", 12L);

        // MutableClock은 2026-10-13 14:00 (Asia/Seoul)에서 시작한다.
        assertEquals("MOCK-20261013-000012", new MockHazardReporter(new MutableClock()).submit(report));
    }
}
