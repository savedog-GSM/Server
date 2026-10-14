package com.example.savedog.Safety.repository;

import com.example.savedog.Safety.domain.SafetyRecord;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SafetyRecordRepository extends JpaRepository<SafetyRecord, Long> {
    List<SafetyRecord> findAllByOrderByAssessedAtDesc(Limit limit);

    List<SafetyRecord> findByLatitudeBetweenAndLongitudeBetweenOrderByAssessedAtDesc(
            double swLat, double neLat, double swLng, double neLng, Limit limit
    );
}
