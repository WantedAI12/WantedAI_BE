package com.perfumeryaicore.domain.supply.repository;

import com.perfumeryaicore.domain.supply.entity.SupplyReviewDecision;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyReviewDecisionRepository extends JpaRepository<SupplyReviewDecision, Long> {

	List<SupplyReviewDecision> findByCandidateIdOrderByCreatedAtDesc(Long candidateId);
}
