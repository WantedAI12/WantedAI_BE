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
		 * AI팀 확인(2026-09-16): 실제 운영 배포 기준으로 숫자 또는 {@code null}만 온다
		 * (예: {@code {"confidence": null, "confidence_kind": "heuristic_only"}}) - 더 이상
		 * 문자열로 오지 않는다. 설명값은 {@link #confidenceKind}로 분리됐다.
		 */
		@JsonProperty("confidence")
		Double confidence,

		@JsonProperty("confidence_kind")
		String confidenceKind,

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
		Deployment deployment,

		/**
		 * AI팀 확인(2026-09-18): 원료 배합비 목록만 보여주던 화면 대신 쓸 수 있는 조향사용
		 * 서술형 설명(콘셉트·원료별 배합 의도·향의 전개·검토 의견·다음 시향 확인 사항을 하나로
		 * 엮은 글). {@code perfumer_notes.text}만 저장·전달한다 - 하위 구조(ingredients 등)는
		 * 이미 {@code recipe}로 따로 갖고 있어 중복 보관하지 않는다.
		 */
		@JsonProperty("perfumer_notes")
		PerfumerNotes perfumerNotes,

		/**
		 * AI팀 확인(2026-09-18): 원료별 농축액/완제품 함량 기록 이유, 가격·가용성, 배합 구성,
		 * 목표 일치도, 시간별 향 강도, 제조 계획·한계를 담은 후보 설명. 구조가 깊고 아직 안정적으로
		 * 확정되지 않아 원문 그대로 보존한다 - 값을 잘라내거나 반올림하지 않는다({@code safety}와
		 * 같은 패턴).
		 */
		@JsonProperty("candidate_explanation")
		JsonNode candidateExplanation,

		/**
		 * AI팀 확인(2026-09-18): 내부 검사·증빙 완전성·출시 검증 분리, 규제 탭, 누락 자료·미확인
		 * 원료, 후속 검토 항목을 담은 안전성 설명. 내부 검사 통과가 전체 규제 승인을 의미하지
		 * 않는다 - 원문 그대로 보존한다.
		 */
		@JsonProperty("safety_explanation")
		JsonNode safetyExplanation
) {

	/** Modal이 안전한 해를 찾지 못한 경우의 상태값. 이때 {@code recipe}는 빈 배열이다. */
	public static final String STATUS_NO_SAFE_MATCH = "no_safe_match";

	public boolean isNoSafeMatch() {
		return STATUS_NO_SAFE_MATCH.equals(status);
	}

	public int recipeSize() {
		return recipe == null ? 0 : recipe.size();
	}

	public String perfumerNotesText() {
		return perfumerNotes == null ? null : perfumerNotes.text();
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

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PerfumerNotes(
			@JsonProperty("text") String text
	) {
	}
}
