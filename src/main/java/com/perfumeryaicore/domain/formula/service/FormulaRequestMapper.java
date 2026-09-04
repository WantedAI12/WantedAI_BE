package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import org.springframework.stereotype.Component;

/**
 * 확정된 향 요청을 조향 AI(Modal) 요청 스키마로 변환한다. 사용자가 입력한 값을 임의로 다시 쓰거나
 * 표현을 추가하지 않는다 — 동일 입력·제약이면 재현 가능한 요청이 나가야 한다.
 */
@Component
public class FormulaRequestMapper {

	public FormulaGenerationRequest toModalRequest(FragranceRequest request) {
		return new FormulaGenerationRequest(
				request.getRawText(),
				request.getRiskTier(),
				request.getMaxIngredientPricePerKg(),
				null,
				null,
				null,
				request.getUsageConcentrationPercent(),
				request.getMaxIngredientCount(),
				false,
				false,
				request.getTargetRegion() != null ? request.getTargetRegion().modalValue() : null,
				request.getProductCategory() != null ? request.getProductCategory().getModalValue() : null);
	}
}
