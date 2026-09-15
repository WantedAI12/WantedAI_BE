package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Modal {@code POST /v2/briefs/clarify} 응답. {@code prepared}는 갱신된 검토 결과(원문 노드 보존). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClarifyBriefResponse(

		@JsonProperty("schema_version")
		String schemaVersion,

		@JsonProperty("previous_result_id")
		String previousResultId,

		@JsonProperty("applied_answer_ids")
		List<String> appliedAnswerIds,

		JsonNode request,

		JsonNode prepared,

		@JsonProperty("new_inference_count")
		Integer newInferenceCount,

		@JsonProperty("state_changed")
		Boolean stateChanged
) {
}
