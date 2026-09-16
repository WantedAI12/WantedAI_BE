package com.perfumeryaicore.domain.supply.dto.response;

import com.perfumeryaicore.domain.supply.entity.SupplyReviewDecision;
import com.perfumeryaicore.domain.supply.entity.SupplyReviewDecisionType;
import java.time.LocalDateTime;

public record SupplyReviewDecisionResponse(
		Long decisionId,
		Long candidateId,
		Long supplyChangeId,
		SupplyReviewDecisionType decision,
		String rationale,
		Long decidedBy,
		LocalDateTime createdAt
) {

	public static SupplyReviewDecisionResponse from(SupplyReviewDecision decision) {
		return new SupplyReviewDecisionResponse(
				decision.getId(),
				decision.getCandidateId(),
				decision.getSupplyChangeId(),
				decision.getDecision(),
				decision.getRationale(),
				decision.getDecidedBy(),
				decision.getCreatedAt());
	}
}
