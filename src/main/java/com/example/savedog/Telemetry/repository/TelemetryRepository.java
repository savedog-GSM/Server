package com.example.savedog.Telemetry.repository;


import com.example.savedog.Telemetry.domain.Telemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TelemetryRepository extends JpaRepository<Telemetry, Long> {
    List<Telemetry> findAllByOrderByRecordedAtDesc();

    List<Telemetry> findByRecordedAtBetweenOrderByRecordedAtAsc(LocalDateTime from, LocalDateTime to);

    Optional<Telemetry> findFirstByOrderByRecordedAtDesc();
}
