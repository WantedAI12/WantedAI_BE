package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.global.client.ModalAiProperties;
import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 확정된 향 요청을 조향 AI(Modal) 요청 스키마로 변환한다. 사용자가 입력한 값을 임의로 다시 쓰거나
 * 표현을 추가하지 않는다 — 동일 입력·제약이면 재현 가능한 요청이 나가야 한다.
 */
@Component
@RequiredArgsConstructor
public class FormulaRequestMapper {

	private final ModalAiProperties modalAiProperties;

	public FormulaGenerationRequest toModalRequest(FragranceRequest request) {
		return new FormulaGenerationRequest(
				request.getRawText(),
				request.getRiskTier(),
				toUsdIngredientPricePerKg(request),
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

	/**
	 * 로션 생성 경로({@link CandidateGenerationService#generateLotion})도 같은 원화→USD 환산이
	 * 필요해 여기서 공용으로 노출한다 - 향수·로션 두 실제 생성 경로가 서로 다른 곳에서 각자
	 * 변환하다 한쪽을 빠뜨리는 일을 막기 위함이다(AI 개발팀 확인, 2026-09-19: v2 리뷰 경로에만
	 * 변환이 적용되고 실제 생성 경로엔 빠져 있었음).
	 */
	public Double toUsdIngredientPricePerKg(FragranceRequest request) {
		return modalAiProperties.krwPerKgToUsd(request.getMaxIngredientPricePerKg());
	}
}
