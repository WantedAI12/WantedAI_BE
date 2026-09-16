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

		/** PRICE_INCREASE/PRICE_DECREASE 외 유형에서 어떤 필드가 바뀌었는지(BE-070). 예: {@code cas_number}. */
		@Size(max = 100)
		String changedField,

		@Size(max = 2000)
		String previousValue,

		@Size(max = 2000)
		String newValue,

		/** 외부 원천의 변경 이벤트 ID. 같은 값으로 다시 등록하면 새 이벤트를 만들지 않고 기존 이벤트를 반환한다. */
		@Size(max = 200)
		String changeSourceId,

		@Size(max = 1000)
		String note
) {
}
