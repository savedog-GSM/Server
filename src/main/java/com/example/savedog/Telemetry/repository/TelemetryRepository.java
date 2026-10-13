package com.example.savedog.Telemetry.repository;


import com.example.savedog.Telemetry.domain.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    List<Telemetry> findByRobotIdOrderByRecordedAtDesc(Long robotId);

    List<Telemetry> findByRobotIdAndRecordedAtBetweenOrderByRecordedAtAsc(Long robotId, LocalDateTime from, LocalDateTime to);

    Optional<Telemetry> findFirstByRobotIdOrderByRecordedAtDesc(Long robotId);
}
