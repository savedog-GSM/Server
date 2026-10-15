package com.example.savedog.Safety.service;

import com.example.savedog.Safety.DTO.SafetySegmentResponse;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.global.common.GeoPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetySegmentBuilderTest {
    private final List<SafetyRecord> records = new ArrayList<>();
    private LocalDateTime clock = LocalDateTime.of(2026, 10, 13, 14, 0);

    @Test
    @DisplayName("같은 등급이 이어지면 한 구간이다")
    void sameLevelMakesOneSegment() {
        visit(1, 1);
        visit(2, 1);
        visit(3, 2);

        List<SafetySegmentResponse> segments = SafetySegmentBuilder.build(records);

        assertEquals(1, segments.size());
        assertEquals(SafetyLevel.SAFE, segments.get(0).level());
        assertEquals(List.of(point(1), point(2), point(3)), segments.get(0).path());
    }

    @Test
    @DisplayName("등급이 바뀌면 직전 지점에서 새 구간을 시작해 선이 이어진다")
    void levelChangeStartsFromPreviousPoint() {
        visit(1, 1);
        visit(2, 1);
        visit(3, 5);
        visit(4, 5);
        visit(5, 3);

        List<SafetySegmentResponse> segments = SafetySegmentBuilder.build(records);

        assertEquals(3, segments.size());
        assertEquals(SafetyLevel.SAFE, segments.get(0).level());
        assertEquals(List.of(point(1), point(2)), segments.get(0).path());
        assertEquals(SafetyLevel.DANGER, segments.get(1).level());
        assertEquals(List.of(point(2), point(3), point(4)), segments.get(1).path());
        assertEquals(SafetyLevel.CAUTION, segments.get(2).level());
        assertEquals(List.of(point(4), point(5)), segments.get(2).path());
    }

    @Test
    @DisplayName("기록 사이가 10초 넘게 비면 구간을 끊는다")
    void longGapBreaksTheLine() {
        visit(1, 1);
        visit(2, 1);
        clock = clock.plusSeconds(30);
        visit(3, 1);
        visit(4, 1);

        List<SafetySegmentResponse> segments = SafetySegmentBuilder.build(records);

        assertEquals(2, segments.size());
        assertEquals(List.of(point(1), point(2)), segments.get(0).path());
        assertEquals(List.of(point(3), point(4)), segments.get(1).path());
    }

    @Test
    @DisplayName("점이 하나뿐인 구간은 선을 그을 수 없으므로 내보내지 않는다")
    void singlePointIsNotASegment() {
        visit(1, 1);
        clock = clock.plusSeconds(30);
        visit(2, 5);

        assertTrue(SafetySegmentBuilder.build(records).isEmpty());
        assertTrue(SafetySegmentBuilder.build(List.of()).isEmpty());
    }

    // 1초 간격으로 한 지점씩 지나간 것처럼 만든다. n은 지점 번호
    private void visit(int n, int riskScore) {
        GeoPoint point = point(n);
        SafetyRecord record = new SafetyRecord(
                null, point.latitude(), point.longitude(), SafetyLevel.fromRiskScore(riskScore), riskScore, null
        );
        ReflectionTestUtils.setField(record, "assessedAt", clock);
        records.add(record);
        clock = clock.plusSeconds(1);
    }

    private GeoPoint point(int n) {
        return new GeoPoint(37.5 + n * 0.0001, 127.0);
    }
}
