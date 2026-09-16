package com.perfumeryaicore.domain.supply.dto.request;

import com.perfumeryaicore.domain.supply.entity.SupplyReviewDecisionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordSupplyReviewDecisionRequest(

		/** 이 결정이 대응하는 공급 변경 이벤트. 있으면 해당 영향 후보 항목을 REVIEWED로 표시한다. */
		Long supplyChangeId,

		@NotNull
		SupplyReviewDecisionType decision,

		@NotBlank
		@Size(max = 2000)
		String rationale
) {
}
