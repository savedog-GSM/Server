package com.example.savedog.Safety.service;

import com.example.savedog.Safety.DTO.SafetySegmentResponse;
import com.example.savedog.Safety.domain.SafetyLevel;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.global.common.GeoPoint;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// 시간순 위험도 기록을 지도에 그릴 선(구간)으로 바꾼다. (기능명세서 F-07)
public final class SafetySegmentBuilder {
    // 기록 사이가 이보다 길게 비면 이어 그리지 않는다.
    private static final Duration MAX_GAP = Duration.ofSeconds(10);

    private SafetySegmentBuilder() {
    }

    // records는 판단 시각 오름차순이어야 한다.
    public static List<SafetySegmentResponse> build(List<SafetyRecord> records) {
        List<SafetySegmentResponse> segments = new ArrayList<>();
        SafetyLevel level = null;
        List<GeoPoint> path = new ArrayList<>();
        SafetyRecord previous = null;

        for (SafetyRecord record : records) {
            GeoPoint point = new GeoPoint(record.getLatitude(), record.getLongitude());
            boolean continues = previous != null
                    && Duration.between(previous.getAssessedAt(), record.getAssessedAt()).compareTo(MAX_GAP) <= 0;

            if (!continues) {
                addIfDrawable(segments, level, path);
                path = new ArrayList<>(List.of(point));
            } else if (record.getRiskLevel() != level) {
                // 등급이 바뀌면 직전 지점에서 새 구간을 시작해 선이 끊기지 않게 한다.
                GeoPoint lastPoint = path.get(path.size() - 1);
                addIfDrawable(segments, level, path);
                path = new ArrayList<>(List.of(lastPoint, point));
            } else {
                path.add(point);
            }
            level = record.getRiskLevel();
            previous = record;
        }
        addIfDrawable(segments, level, path);
        return segments;
    }

    // 점이 하나뿐이면 선을 그을 수 없으므로 버린다.
    private static void addIfDrawable(List<SafetySegmentResponse> segments, SafetyLevel level, List<GeoPoint> path) {
        if (level != null && path.size() >= 2) {
            segments.add(new SafetySegmentResponse(level, List.copyOf(path)));
        }
    }
}
