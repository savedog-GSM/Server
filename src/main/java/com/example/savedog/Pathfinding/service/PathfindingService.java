package com.example.savedog.Pathfinding.service;

import com.example.savedog.Pathfinding.DTO.RobotTelemetry;
import com.example.savedog.Pathfinding.domain.Pathfinding;
import com.example.savedog.Pathfinding.repository.PathfindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PathfindingService {
    private final PathfindingRepository pathfindingRepository;
    private final WebSocketService webSocketService;

    @Transactional
    public void receive(RobotTelemetry telemetry){
        Pathfinding pathfinding = new Pathfinding(telemetry.robotId(),
        telemetry.latitude(),
        telemetry.longitude(),
        telemetry.speed());
        pathfindingRepository.save(pathfinding);
        webSocketService.broadcast(telemetry);
    }
}
