package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Modal {@code POST /v1/applications/body-lotion/design} 요청 본문.
 *
 * <p>1단계(팀 확인 완료): 중첩 옵션 6종({@code storage}/{@code skin_exposure}/
 * {@code transition_schedule}/{@code base_design}/{@code dose_trials}/{@code process})은 이번
 * 버전에 넣지 않는다 - 기본 참조 로션·기본 계산 조건을 그대로 쓴다는 뜻이다. 생략해도
 * Modal이 알아서 기본 객체를 적용하며(예: skin_exposure는 피부 유형 all·면적 1000cm²·
 * 체중 60kg·1일 1회 적용), 실제 적용된 값은 응답 원문에 그대로 남으므로 원문 보존으로
 * 화면에서 확인 가능하다(read-time 매퍼는 후속 배치).
 *
 * <p>{@code fragrance_concentration_percent}: 프론트 보고(2026-09-18) - 사용자가 입력한 농도가
 * 로션 생성에 전달되지 않고 Modal 기본값이 쓰이고 있었다. 라이브 호출로 이 필드명을 직접
 * 확인했다({@code estimation.application_context.fragrance_concentration_percent}로 그대로
 * 반영됨). {@code FragranceRequest.usageConcentrationPercent}를 그대로 실어 보낸다 - 표준
 * 향수(0~30%)와 로션(0.01~3.0%)의 유효 범위가 다르지만, 범위를 벗어난 값의 판정은 임의로
 * 보정하지 않고 Modal에 맡긴다(재해석 없이 그대로 전달 원칙).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LotionEstimateRequest(

		@JsonProperty("brief")
		String brief,

		@JsonProperty("max_risk_tier")
		Integer maxRiskTier,

		@JsonProperty("max_ingredient_price_per_kg")
		Double maxIngredientPricePerKg,

		@JsonProperty("fragrance_concentration_percent")
		Double fragranceConcentrationPercent,

		/**
		 * AI팀 확인(2026-09-19): 선택 향 계열. {@code brief} 원문을 덧붙이거나 덮어쓰지 않고
		 * 별도 JSON 항목으로 함께 전달된다. 저장된 {@code request.accords()}를 재해석 없이
		 * 그대로 전달한다.
		 */
		@JsonProperty("accords")
		List<String> accords
) {

	public static LotionEstimateRequest of(String brief, Integer maxRiskTier, Double maxIngredientPricePerKg,
			Double fragranceConcentrationPercent, List<String> accords) {
		return new LotionEstimateRequest(
				brief, maxRiskTier, maxIngredientPricePerKg, fragranceConcentrationPercent, accords);
	}
}
