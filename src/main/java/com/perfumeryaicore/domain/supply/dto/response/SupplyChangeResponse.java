package com.perfumeryaicore.domain.supply.dto.response;

import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.supply.entity.SupplyChange;
import com.perfumeryaicore.domain.supply.entity.SupplyChangeType;
import java.time.LocalDateTime;

/**
 * 공급 변경 이벤트 + 분석 상태. 분석은 등록 시 동기적으로 끝나므로 {@code analysisStatus}는 항상 SUCCEEDED.
 */
public record SupplyChangeResponse(
		Long changeId,
		Long projectId,
		String ingredientId,
		SupplyChangeType changeType,
		Double previousPricePerKg,
		Double newPricePerKg,
		String note,
		JobStatus analysisStatus,
		int affectedCandidateCount,
		LocalDateTime createdAt
) {

	public static SupplyChangeResponse from(SupplyChange change) {
		return new SupplyChangeResponse(
				change.getId(),
				change.getProjectId(),
				change.getIngredientExternalId(),
				change.getChangeType(),
				change.getPreviousPricePerKg(),
				change.getNewPricePerKg(),
				change.getNote(),
				change.getAnalysisStatus(),
				change.getAffectedCandidateCount(),
				change.getCreatedAt());
	}
}
