package com.example.savedog.Pathfinding.service;

import com.example.savedog.Pathfinding.DTO.PathSearchRequest;
import com.example.savedog.Pathfinding.DTO.PathSearchResponse;
import com.example.savedog.Pathfinding.domain.PathMode;
import com.example.savedog.Pathfinding.planner.PathPlanner;
import com.example.savedog.Safety.domain.SafetyRecord;
import com.example.savedog.Safety.repository.SafetyRecordRepository;
import com.example.savedog.global.exception.BusinessException;
import com.example.savedog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PathfindingService {
    private final PathPlanner pathPlanner;
    private final SafetyRecordRepository safetyRecordRepository;

    @Transactional(readOnly = true)
    public PathSearchResponse search(PathSearchRequest request) {
        if (request.start() == null || request.end() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        PathMode mode = request.mode() == null ? PathMode.SAFEST : request.mode();
        List<SafetyRecord> records = safetyRecordRepository.findAll();
        return pathPlanner.plan(request.start(), request.end(), mode, records)
                .map(path -> PathSearchResponse.of(mode, path))
                .orElseThrow(() -> new BusinessException(ErrorCode.PATH_NOT_FOUND));
    }
}
