package com.example.savedog.Safety.repository;

import com.example.savedog.Safety.domain.SafetyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SafetyRecordRepository extends JpaRepository<SafetyRecord, Long> {
    List<SafetyRecord> findByRobotIdOrderByAssessedAtAsc(Long robotId);

    List<SafetyRecord> findByLatitudeBetweenAndLongitudeBetween(double swLat, double neLat, double swLng, double neLng);

    Optional<SafetyRecord> findFirstByRobotIdOrderByAssessedAtDesc(Long robotId);
}
