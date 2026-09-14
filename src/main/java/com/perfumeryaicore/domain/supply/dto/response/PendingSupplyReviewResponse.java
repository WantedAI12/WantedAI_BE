package com.perfumeryaicore.domain.supply.dto.response;

import com.perfumeryaicore.domain.supply.entity.SupplyChange;
import com.perfumeryaicore.domain.supply.entity.SupplyChangeAffectedCandidate;
import com.perfumeryaicore.domain.supply.entity.SupplyChangeType;
import java.time.LocalDateTime;

/**
 * 재검토 알림 화면(BE-090~098 일부)에 쓰이는 한 줄. 아직 재검토(PENDING_REVIEW)되지 않은
 * 영향 후보를, 그 원인이 된 공급 변경 정보와 함께 보여준다.
 */
public record PendingSupplyReviewResponse(
		Long supplyChangeId,
		String ingredientId,
		SupplyChangeType changeType,
		Long candidateId,
		Long candidateVersionId,
		Double ingredientConcentratePercent,
		LocalDateTime notifiedAt
) {

	public static PendingSupplyReviewResponse of(SupplyChangeAffectedCandidate affected, SupplyChange change) {
		return new PendingSupplyReviewResponse(
				change.getId(),
				change.getIngredientExternalId(),
				change.getChangeType(),
				affected.getCandidateId(),
				affected.getCandidateVersionId(),
				affected.getIngredientConcentratePercent(),
				affected.getCreatedAt());
	}
}
