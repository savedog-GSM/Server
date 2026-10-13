package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.global.common.GeoPoint;

import java.util.List;
import java.util.Optional;

/**
 * 로보독이 정찰하며 쌓은 안전도 기록 위에서 출발지→도착지 경로를 찾는다.
 * 알고리즘(A*, 다익스트라, AI 등)을 바꾸려면 이 인터페이스의 구현체만 교체하면 된다.
 * 구현체를 둘 이상 두면 실제로 쓸 쪽에 @Primary를 붙인다.
 */
public interface PathPlanner {
    // 갈 수 있는 경로가 없으면 Optional.empty()
    Optional<PlannedPath> plan(GeoPoint start, GeoPoint end, List<SafetyRecord> safetyRecords);
}
