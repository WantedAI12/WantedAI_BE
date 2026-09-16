package com.perfumeryaicore.domain.supply.dto.response;

import com.perfumeryaicore.domain.supply.entity.SupplyChangeAffectedCandidate;
import com.perfumeryaicore.domain.supply.entity.SupplyReviewStatus;

public record AffectedCandidateResponse(
		Long candidateId,
		Long candidateVersionId,
		Double ingredientConcentratePercent,
		SupplyReviewStatus reviewStatus
) {

	public static AffectedCandidateResponse from(SupplyChangeAffectedCandidate affected) {
		return new AffectedCandidateResponse(
				affected.getCandidateId(),
				affected.getCandidateVersionId(),
				affected.getIngredientConcentratePercent(),
				affected.getReviewStatus());
	}
}
