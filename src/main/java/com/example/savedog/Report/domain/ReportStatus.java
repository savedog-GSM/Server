package com.example.savedog.Report.domain;

public enum ReportStatus {
    // 자동으로 만들어진 직후. 관제자 확인을 기다린다.
    PENDING,
    // 민원 창구에 접수됨
    SUBMITTED,
    // 관제자가 오탐으로 판단
    DISMISSED,
    // 창구로 보내다 실패. 다시 제출하거나 기각할 수 있다.
    FAILED
}
