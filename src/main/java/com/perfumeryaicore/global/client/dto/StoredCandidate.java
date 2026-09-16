package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * {@code /v2/formulas/compare}·{@code /v2/briefs/revise}가 요구하는 저장 후보 스냅샷.
 * {@code evaluation}은 {@code /v2/formulas/evaluate}·{@code /reassess}가 돌려준 평가 결과 전체
 * (BE가 저장 없이 되돌려준 {@link EvaluationResponse} 원문 그대로) - 새로 만들지 않고 호출부가
 * 이전에 받은 값을 그대로 넣어 보존한다.
 */
public record StoredCandidate(

		JsonNode evaluation,

		@JsonProperty("candidate_id")
		String candidateId,

		@JsonProperty("backend_version_id")
		String backendVersionId
) {
}
