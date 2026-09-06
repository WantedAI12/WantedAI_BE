package com.perfumeryaicore.domain.evidence.repository;

import com.perfumeryaicore.domain.evidence.entity.SensoryTestResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensoryTestResultRepository extends JpaRepository<SensoryTestResult, Long> {

	List<SensoryTestResult> findBySensoryTestIdOrderByCreatedAtDesc(Long sensoryTestId);

	List<SensoryTestResult> findBySensoryTestIdIn(List<Long> sensoryTestIds);
}
