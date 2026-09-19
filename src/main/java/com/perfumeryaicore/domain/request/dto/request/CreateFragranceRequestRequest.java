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

		/**
		 * 제품 종류. 향수(오 드 코롱·뚜왈렛·퍼퓸)는 {@code usageConcentrationPercent}가 있으면 농도로 자동 결정된다 -
		 * 5% 미만 코롱, 5% 이상 15% 미만 뚜왈렛, 15% 이상 퍼퓸. 프론트는 "향수"로 보내기만 하면 되고 결정된 값은
		 * 응답의 {@code structuredIntent.productCategory}로 읽는다. 농도가 없으면 보낸 값을 유지하고, 바디로션 등은 그대로다.
		 */
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

		/** 원료 1kg당 최대 단가, 원화(KRW/kg) 기준 - 화면 표시 단위와 동일하다. 상한 없음. */
		@DecimalMin(value = "0", inclusive = false)
		Double maxIngredientPricePerKg,

		List<@NotBlank String> accords
) {
}
