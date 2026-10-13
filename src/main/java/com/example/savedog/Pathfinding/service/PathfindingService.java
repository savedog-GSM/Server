package com.example.savedog.Pathfinding.service;

import com.example.savedog.Pathfinding.DTO.PathSearchRequest;
import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.Pathfinding.planner.PathPlanner;
import com.example.savedog.Safety.repository.SafetyRecordRepository;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PathfindingService {
    private final PathPlanner pathPlanner;
    private final SafetyRecordRepository safetyRecordRepository;

    @Transactional(readOnly = true)
    public PathSearchResponse search(PathSearchRequest request) {
        // TODO(F-07): 1) 탐색에 쓸 안전도 기록 조회
        //             2) pathPlanner.plan(start, end, records)
        //             3) 결과가 없으면 BusinessException(PATH_NOT_FOUND), 있으면 PathSearchResponse.from(...)
        throw new BusinessException(ErrorCode.NOT_IMPLEMENTED);
    }
}
