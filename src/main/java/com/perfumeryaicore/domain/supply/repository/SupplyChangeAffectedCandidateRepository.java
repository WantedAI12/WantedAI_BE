package com.perfumeryaicore.domain.supply.repository;

import com.perfumeryaicore.domain.supply.entity.SupplyChangeAffectedCandidate;
import com.perfumeryaicore.domain.supply.entity.SupplyReviewStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyChangeAffectedCandidateRepository
		extends JpaRepository<SupplyChangeAffectedCandidate, Long> {

	List<SupplyChangeAffectedCandidate> findBySupplyChangeIdOrderByCreatedAtAsc(Long supplyChangeId);

	Optional<SupplyChangeAffectedCandidate> findBySupplyChangeIdAndCandidateId(Long supplyChangeId, Long candidateId);

	List<SupplyChangeAffectedCandidate> findBySupplyChangeIdInAndReviewStatusOrderByCreatedAtDesc(
			List<Long> supplyChangeIds, SupplyReviewStatus reviewStatus);
}
