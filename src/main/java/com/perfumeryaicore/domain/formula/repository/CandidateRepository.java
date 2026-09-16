package com.perfumeryaicore.domain.formula.repository;

import com.perfumeryaicore.domain.formula.entity.Candidate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {

	List<Candidate> findByRequestIdOrderByCreatedAtDesc(Long requestId);

	List<Candidate> findByProjectIdIn(List<Long> projectIds);
}
