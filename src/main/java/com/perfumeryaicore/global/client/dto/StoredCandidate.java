package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * {@code /v2/formulas/compare}·{@code /v2/briefs/revise}가 요구하는 저장 후보 스냅샷.
 * {@code evaluation}은 evaluate/reassess가 돌려준 평가 결과 원문(스키마 미확정, 완전 불투명) -
 * 우리가 만들어내지 않고 그대로 보존했다가 되돌려준다.
 */
public record StoredCandidate(

		JsonNode evaluation,

		@JsonProperty("candidate_id")
		String candidateId,

		@JsonProperty("backend_version_id")
		String backendVersionId
) {
}
