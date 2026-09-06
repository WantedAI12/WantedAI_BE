package com.perfumeryaicore.domain.evidence.repository;

import com.perfumeryaicore.domain.evidence.entity.EvidenceReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceReportRepository extends JpaRepository<EvidenceReport, Long> {
}
