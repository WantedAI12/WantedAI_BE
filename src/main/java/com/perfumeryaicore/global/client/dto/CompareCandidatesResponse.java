package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/formulas/compare} 응답. {@code candidates}/{@code pairs}는 후보별
 * 시간대별 유사도·비용 등 깊고 가변적인 구조라 원문 노드로 보존한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CompareCandidatesResponse(

		@JsonProperty("schema_version")
		String schemaVersion,

		List<JsonNode> candidates,

		List<JsonNode> pairs,

		@JsonProperty("automatic_ranking_performed")
		Boolean automaticRankingPerformed,

		@JsonProperty("recommendation_decision_performed")
		Boolean recommendationDecisionPerformed,

		@JsonProperty("new_inference_count")
		Integer newInferenceCount,

		@JsonProperty("state_changed")
		Boolean stateChanged,

		JsonNode provenance,

		@JsonProperty("result_id")
		String resultId
) {
}
