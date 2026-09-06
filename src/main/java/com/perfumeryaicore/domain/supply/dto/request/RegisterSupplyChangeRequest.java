package com.perfumeryaicore.domain.supply.dto.request;

import com.perfumeryaicore.domain.supply.entity.SupplyChangeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 원료 공급 조건 변경 등록. 경로 {@code /ingredients/{ingredientId}/supply-changes}에는 projectId가
 * 없었으므로(권한·테넌트 격리를 위해 필요) 바디로 받는다 — 문서 델타 반영 대상.
 */
public record RegisterSupplyChangeRequest(

		@NotNull
		Long projectId,

		@NotNull
		SupplyChangeType changeType,

		Double previousPricePerKg,

		Double newPricePerKg,

		@Size(max = 1000)
		String note
) {
}
