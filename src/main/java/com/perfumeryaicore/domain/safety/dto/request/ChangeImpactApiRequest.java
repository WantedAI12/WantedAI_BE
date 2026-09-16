package com.perfumeryaicore.domain.safety.dto.request;

import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 원료·공급 근거 버전이 바뀐 뒤, 후보의 현재 배합이 그 이전 근거 버전({@code previousEvidenceVersion})
 * 대비 여전히 유효한지 재평가한다({@code /v2/formulas/change-impact}). 배합 원료는
 * {@link AssessEvidenceApiRequest}와 같이 후보의 현재 버전에서 가져온다.
 */
public record ChangeImpactApiRequest(

		@NotBlank
		String previousEvidenceVersion,

		@NotNull
		TargetRegion targetRegion,

		@NotNull
		ProductCategory productCategory,

		@NotNull @Positive
		Double productConcentrationPercent,

		@NotNull @Positive
		Double maxFormulaCostPerKg,

		@Positive
		Double maxIngredientPricePerKg,

		@Positive
		Double finishedBatchMassG,

		@Positive
		Integer maximumLeadTimeDays,

		@Positive
		Double maximumPurchaseCostUsd
) {
}
