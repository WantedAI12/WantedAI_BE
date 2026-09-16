package com.perfumeryaicore.domain.safety.dto.request;

import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 후보의 현재 배합을 대상으로 규제·공급 근거를 평가한다({@code /v2/formulas/assess-evidence}).
 * 배합 원료(lines)는 후보의 현재 버전에서 가져오므로 여기서는 받지 않는다 - 지역/제품/비용
 * 조건만 명시한다(Candidate/FragranceRequest에 없는 값도 있어 그대로 저장된 값을 재사용하지 않는다).
 */
public record AssessEvidenceApiRequest(

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
