package com.perfumeryaicore.domain.evidence.repository;

import com.perfumeryaicore.domain.evidence.entity.SensoryTest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SensoryTestRepository extends JpaRepository<SensoryTest, Long> {

	List<SensoryTest> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
}
