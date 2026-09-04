package com.perfumeryaicore.domain.formula.repository;

import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateVersionRepository extends JpaRepository<CandidateVersion, Long> {

	List<CandidateVersion> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
}
