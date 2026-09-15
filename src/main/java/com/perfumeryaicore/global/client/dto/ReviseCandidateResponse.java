package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/briefs/revise} 응답. 새 후보를 저장·승인하지 않으므로, {@code nextOperation}이
 * 안내하는 다음 단계(재확인 후 evaluate/reassess)를 그대로 따라야 한다 - 여기서 받은 {@code prepared}를
 * 곧바로 확정된 후보처럼 표시하면 안 된다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviseCandidateResponse(

		@JsonProperty("schema_version")
		String schemaVersion,

		JsonNode request,

		JsonNode prepared,

		JsonNode adjustments,

		@JsonProperty("new_inference_count")
		Integer newInferenceCount,

		@JsonProperty("state_changed")
		Boolean stateChanged,

		@JsonProperty("next_operation")
		String nextOperation,

		String scope
) {
}
