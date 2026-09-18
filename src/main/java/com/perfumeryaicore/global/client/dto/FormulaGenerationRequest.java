package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Modal {@code POST /v1/formulas} 요청 본문.
 *
 * <p>Modal 스키마는 정의되지 않은 필드를 거부하므로({@code additionalProperties: false})
 * 이 레코드에 없는 값을 임의로 추가하지 않는다. {@code null} 필드는 직렬화에서 제외되어
 * Modal의 확정된 기본값이 적용된다. {@code brief}만 필수.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FormulaGenerationRequest(

		@JsonProperty("brief")
		String brief,

		@JsonProperty("max_risk_tier")
		Integer maxRiskTier,

		@JsonProperty("max_ingredient_price_per_kg")
		Double maxIngredientPricePerKg,

		@JsonProperty("max_formula_cost_per_kg")
		Double maxFormulaCostPerKg,

		@JsonProperty("min_availability")
		Double minAvailability,

		@JsonProperty("target_similarity")
		Double targetSimilarity,

		@JsonProperty("product_concentration_percent")
		Double productConcentrationPercent,

		@JsonProperty("max_ingredients")
		Integer maxIngredients,

		@JsonProperty("enable_registry_trace_candidates")
		Boolean enableRegistryTraceCandidates,

		@JsonProperty("experimental_disable_safety")
		Boolean experimentalDisableSafety,

		@JsonProperty("target_region")
		String targetRegion,

		@JsonProperty("product_category")
		String productCategory,

		/**
		 * AI팀 확인(2026-09-19): 선택 향 계열. {@code brief} 원문을 덧붙이거나 덮어쓰지 않고
		 * 별도 JSON 항목으로 함께 전달된다 - 언어 해석·생성 요청 캐시에도 반영되어 같은 원문이라도
		 * accords가 다르면 다른 요청으로 취급된다. 문자열 32개까지, 항목당 100자까지 허용.
		 * 저장된 {@code request.accords()}를 재해석 없이 그대로 전달한다.
		 */
		@JsonProperty("accords")
		List<String> accords
) {

	/**
	 * 일반 서비스 요청 기본값: 위험등급 1, 전체 레지스트리 실험 비활성화, 안전 비활성화 사용 안 함.
	 * 나머지 제약(가격·가용성·유사도·농도·원료 수)은 Modal 기본값에 위임한다.
	 */
	public static FormulaGenerationRequest standard(String brief, String targetRegion, String productCategory,
			Double maxIngredientPricePerKg, Double productConcentrationPercent, Integer maxIngredients) {
		return new FormulaGenerationRequest(
				brief,
				1,
				maxIngredientPricePerKg,
				null,
				null,
				null,
				productConcentrationPercent,
				maxIngredients,
				false,
				false,
				targetRegion,
				productCategory,
				List.of());
	}
}
