package com.perfumeryaicore.domain.request.dto.request;

import com.perfumeryaicore.domain.request.entity.Intensity;
import com.perfumeryaicore.domain.request.entity.Longevity;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 자연어 향 요청 생성. {@code rawText} 외에는 이후 보완(PATCH)해도 된다.
 * 지원하지 않는 값은 임의로 추정하지 않고 검증 오류로 되돌린다.
 */
public record CreateFragranceRequestRequest(

		@NotBlank
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

		List<@NotBlank String> accords
) {
}
