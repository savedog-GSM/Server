package com.example.savedog.Report.repository;

import com.example.savedog.Report.domain.HazardReport;
import com.example.savedog.Report.domain.ReportStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HazardReportRepository extends JpaRepository<HazardReport, Long> {
    List<HazardReport> findAllByOrderByCreatedAtDesc(Limit limit);

    List<HazardReport> findByStatusOrderByCreatedAtDesc(ReportStatus status, Limit limit);

    List<HazardReport> findByCreatedAtAfter(LocalDateTime since);
}
