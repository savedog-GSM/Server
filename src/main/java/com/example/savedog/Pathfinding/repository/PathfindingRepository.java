package com.example.savedog.Pathfinding.repository;


import com.example.savedog.Pathfinding.domain.Pathfinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PathfindingRepository extends JpaRepository<Pathfinding, Long> {
    List<Pathfinding> findByRobotIdOrderByRecordedAtDesc(Long robotId);
}
