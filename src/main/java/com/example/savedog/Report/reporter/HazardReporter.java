package com.example.savedog.Report.reporter;

import com.example.savedog.Report.domain.HazardReport;

/**
 * 신고를 외부 민원 창구로 보내는 부분. 창구가 정해지면 이 인터페이스의 구현체만 바꾼다.
 * (연계 API, 담당 기관 이메일 등. 기능명세서 F-14)
 */
public interface HazardReporter {
    // 접수에 성공하면 창구가 준 접수번호를 돌려준다. 실패하면 BusinessException(REPORT_SUBMIT_FAILED)
    String submit(HazardReport report);
}
