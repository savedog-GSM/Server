package com.example.savedog.Safety.repository;

import com.example.savedog.Safety.domain.SafetyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SafetyRecordRepository extends JpaRepository<SafetyRecord, Long> {
    List<SafetyRecord> findAllByOrderByAssessedAtAsc();

    List<SafetyRecord> findByLatitudeBetweenAndLongitudeBetween(double swLat, double neLat, double swLng, double neLng);

    Optional<SafetyRecord> findFirstByOrderByAssessedAtDesc();
}
