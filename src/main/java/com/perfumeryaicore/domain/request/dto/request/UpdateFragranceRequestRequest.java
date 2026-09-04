package com.perfumeryaicore.domain.request.dto.request;

import com.perfumeryaicore.domain.request.entity.Intensity;
import com.perfumeryaicore.domain.request.entity.Longevity;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 누락 항목 보완 / 구조화 결과 수정. 모든 필드 선택적이며 {@code null}이 아닌 값만 반영된다.
 * 빈 리스트를 명시적으로 보내면 accords를 비운다.
 */
public record UpdateFragranceRequestRequest(

		@Size(max = 2000)
		String rawText,

		ProductCategory productCategory,

		TargetRegion targetRegion,

		@Min(1) @Max(2)
		Integer riskTier,

		Intensity intensity,

		Longevity longevity,

		@DecimalMin(value = "0", inclusive = false) @DecimalMax("30")
		Double usageConcentrationPercent,

		@Min(6) @Max(20)
		Integer maxIngredientCount,

		@DecimalMin(value = "0", inclusive = false) @DecimalMax("300")
		Double maxIngredientPricePerKg,

		List<String> accords
) {
}
