package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/formulas/evaluate}·{@code /v2/formulas/reassess} 200 응답
 * (AI 개발팀 확인, 2026-09-16 — {@code rd-candidates-2} 스키마).
 *
 * <p>{@code diagnosticOnly=true}인 응답은 {@code candidates}가 비어 있고 결과는
 * {@code diagnosticCandidates}에 담긴다 - 정상 운영 추천과 다른 배열이니 절대 섞어 쓰면 안 된다.
 * {@code status="abstained"}는 계산 실패가 아니라 운영 추천 보류를 뜻하며, HTTP 200과 모순되지
 * 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvaluationResponse(

		@JsonProperty("schema_version")
		String schemaVersion,

		@JsonProperty("review_id")
		String reviewId,

		JsonNode contract,

		String status,

		/** 일반(비진단) 모드에서 운영 승인 가능한 추천 후보. 진단 모드에서는 항상 비어 있다. */
		List<JsonNode> candidates,

		@JsonProperty("diagnostic_candidates")
		List<DiagnosticCandidate> diagnosticCandidates,

		@JsonProperty("state_changed")
		Boolean stateChanged,

		@JsonProperty("manufacturing_approval")
		Boolean manufacturingApproval,

		/** 화면에서 "진단 결과"로 구분 표시하는 기준 필드 - 일반 응답에서는 생략될 수 있다. */
		@JsonProperty("diagnostic_only")
		Boolean diagnosticOnly,

		String scope,

		@JsonProperty("input_snapshot")
		JsonNode inputSnapshot,

		@JsonProperty("result_id")
		String resultId
) {

	public boolean isDiagnostic() {
		return Boolean.TRUE.equals(diagnosticOnly);
	}
}
