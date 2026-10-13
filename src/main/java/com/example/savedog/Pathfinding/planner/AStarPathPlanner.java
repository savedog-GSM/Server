package com.example.savedog.Pathfinding.planner;

import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.global.common.GeoPoint;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// 안전도를 비용에 반영한 A* 기본 구현체. 방식 초안은 기능 명세서 6.2 참고
@Component
public class AStarPathPlanner implements PathPlanner {

    @Override
    public Optional<PlannedPath> plan(GeoPoint start, GeoPoint end, List<SafetyRecord> safetyRecords) {
        // TODO(F-07): 기록을 격자 셀로 묶어 그래프 구성 → 위험할수록 비용이 큰 A* 탐색 → 좌표 목록으로 복원
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }
}
