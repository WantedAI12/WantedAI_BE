package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v1/formulas} 응답에서 백엔드가 직접 사용하는 필드만 추린 뷰.
 *
 * <p>Modal 응답에는 100개 이상의 필드가 있고 여기 없는 값도 많다. 원본 전체는
 * {@link com.perfumeryaicore.global.client.PerfumeryAiResult#rawJson()}에 그대로 보관하고,
 * 이 레코드는 검증·화면 구성에 필요한 최소 구조만 노출한다. 농도값은 반올림하거나
 * 합계를 보정하지 않고 Modal이 준 값을 그대로 담는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FormulaGenerationResponse(

		@JsonProperty("status")
		String status,

		@JsonProperty("message")
		String message,

		@JsonProperty("formula_id")
		String formulaId,

		/**
		 * 항상 숫자는 아니다 - {@code "heuristic_only"} 같은 문자열 상태값으로 올 때가 있다
		 * (실서버 확인 완료). 숫자였던 값도 문자열로 그대로 보존하며, 임의로 숫자로 바꾸거나
		 * 0으로 대체하지 않는다. 숫자 신뢰도가 필요하면 별도 필드로 계약해야 한다.
		 */
		@JsonProperty("confidence")
		String confidence,

		@JsonProperty("estimated_concentrate_cost_per_kg")
		Double estimatedConcentrateCostPerKg,

		@JsonProperty("recipe")
		List<RecipeLine> recipe,

		@JsonProperty("temporal_timepoints_minutes")
		List<Integer> temporalTimepointsMinutes,

		@JsonProperty("temporal_profile")
		List<JsonNode> temporalProfile,

		@JsonProperty("ingredient_temporal_profile")
		List<JsonNode> ingredientTemporalProfile,

		@JsonProperty("temporal_concentration_basis")
		JsonNode temporalConcentrationBasis,

		@JsonProperty("temporal_model_claim_boundary")
		String temporalModelClaimBoundary,

		@JsonProperty("safety")
		JsonNode safety,

		@JsonProperty("scientific_model_version")
		String scientificModelVersion,

		/** V80(2026-09-15)에서 새로 추가된 필드 - 이전 버전(V69) 응답에는 없었다. */
		@JsonProperty("scientific_sampling_version")
		String scientificSamplingVersion,

		@JsonProperty("deployment")
		Deployment deployment
) {

	/** Modal이 안전한 해를 찾지 못한 경우의 상태값. 이때 {@code recipe}는 빈 배열이다. */
	public static final String STATUS_NO_SAFE_MATCH = "no_safe_match";

	public boolean isNoSafeMatch() {
		return STATUS_NO_SAFE_MATCH.equals(status);
	}

	public int recipeSize() {
		return recipe == null ? 0 : recipe.size();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RecipeLine(
			@JsonProperty("ingredient_id") String ingredientId,
			@JsonProperty("name") String name,
			@JsonProperty("pyramid") String pyramid,
			@JsonProperty("concentrate_percent") Double concentratePercent,
			@JsonProperty("finished_product_percent") Double finishedProductPercent,
			@JsonProperty("price_per_kg") Double pricePerKg,
			@JsonProperty("availability") Double availability
	) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Deployment(
			@JsonProperty("provider") String provider,
			@JsonProperty("runtime") String runtime,
			@JsonProperty("gpu_required") Boolean gpuRequired,
			@JsonProperty("wheel_sha256") String wheelSha256,
			@JsonProperty("registry_sha256") String registrySha256,
			@JsonProperty("registry_connected_total") Integer registryConnectedTotal
	) {
	}
}
