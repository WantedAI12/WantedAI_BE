package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * {@code EvaluationResponse.diagnosticCandidates}의 항목 하나(AI 개발팀 확인, 2026-09-16).
 *
 * <p>{@code recommendationAllowed}는 진단 후보에서는 항상 {@code false}다 - 일반 추천 목록이나
 * 승인된 조향식으로 취급하면 안 된다. {@code result.status}가 {@code no_safe_match}이면
 * {@code result.recipe}는 비어 있고 {@code result.closest_candidate}에만 배합이 담긴다 - 화면에
 * 표시할 때 이 둘을 섞으면 안 된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosticCandidate(

		@JsonProperty("candidate_id")
		String candidateId,

		@JsonProperty("formula_id")
		String formulaId,

		String status,

		@JsonProperty("assessment_kind")
		String assessmentKind,

		@JsonProperty("target_match_score")
		Double targetMatchScore,

		@JsonProperty("target_match_unit")
		String targetMatchUnit,

		@JsonProperty("product_concentration_percent")
		Double productConcentrationPercent,

		@JsonProperty("material_count")
		Integer materialCount,

		@JsonProperty("recommendation_allowed")
		Boolean recommendationAllowed,

		/** {@code status}/{@code recipe}/{@code closest_candidate} 등 계산 상세. 원문 그대로 보존. */
		JsonNode result,

		@JsonProperty("rd_gates")
		JsonNode rdGates,

		@JsonProperty("evidence_assessment")
		JsonNode evidenceAssessment,

		JsonNode regulatory,

		JsonNode evidence,

		JsonNode runtime
) {

	/** 정상 사용 가능한 추천으로 착각하지 않도록 호출부가 항상 확인해야 하는 값. */
	public boolean isRecommendationAllowed() {
		return Boolean.TRUE.equals(recommendationAllowed);
	}
}
