package com.example.savedog.Safety.service;

import com.example.savedog.Safety.analyzer.SafetyAssessment;
import com.example.savedog.Safety.domain.SafetyRecord;

// 위험도 판단 한 번의 결과. record는 위치가 없어 기록으로 남기지 않았으면 null
public record Assessed(
        SafetyAssessment assessment,
        SafetyRecord record
) {
}
