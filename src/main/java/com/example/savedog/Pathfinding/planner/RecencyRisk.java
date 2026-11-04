package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Safety.domain.SafetyRecord;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 한 칸에 쌓인 기록들로 그 칸의 위험도를 정한다. 지나간 회차별 평균을 구하고, 최근 회차일수록 2배씩 무겁게 쳐서 평균한다.
final class RecencyRisk {
    // 같은 칸의 기록이 이보다 길게 비면 다른 회차(다시 지나감)로 본다.
    private static final Duration PASS_GAP = Duration.ofSeconds(60);

    private RecencyRisk() {
    }

    static double of(List<SafetyRecord> records) {
        List<SafetyRecord> sorted = records.stream()
                .sorted(Comparator.comparing(SafetyRecord::getAssessedAt))
                .toList();
        List<Double> passAverages = new ArrayList<>();
        double sum = 0;
        int count = 0;
        LocalDateTime previousAt = null;
        for (SafetyRecord record : sorted) {
            boolean newPass = previousAt != null
                    && Duration.between(previousAt, record.getAssessedAt()).compareTo(PASS_GAP) > 0;
            if (newPass) {
                passAverages.add(sum / count);
                sum = 0;
                count = 0;
            }
            sum += record.getRiskScore();
            count++;
            previousAt = record.getAssessedAt();
        }
        passAverages.add(sum / count);

        double weightedSum = 0;
        double totalWeight = 0;
        double weight = 1;
        for (int i = passAverages.size() - 1; i >= 0; i--) {
            weightedSum += passAverages.get(i) * weight;
            totalWeight += weight;
            weight /= 2;
        }
        return weightedSum / totalWeight;
    }
}
