package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/** Modal {@code POST /v2/briefs/clarify} 요청 본문. {@code prepared_result_id}는 직전 prepare/clarify의 {@code result_id}를 그대로 넣는다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClarifyBriefRequest(

		@JsonProperty("request")
		JsonNode request,

		@JsonProperty("evidence_policy")
		JsonNode evidencePolicy,

		@JsonProperty("lines")
		JsonNode lines,

		@JsonProperty("revision")
		Integer revision,

		@JsonProperty("diagnostic_only")
		Boolean diagnosticOnly,

		@JsonProperty("prepared_result_id")
		String preparedResultId,

		@JsonProperty("answers")
		JsonNode answers
) {

	public static ClarifyBriefRequest of(
			JsonNode request, JsonNode evidencePolicy, String preparedResultId, JsonNode answers) {
		return new ClarifyBriefRequest(request, evidencePolicy, null, null, null, preparedResultId, answers);
	}

	/** {@code diagnostic_only} 모드로 시작한 prepare를 이어가는 clarify - 모드가 유지되어야 review_id를 재사용할 수 있다. */
	public static ClarifyBriefRequest of(
			JsonNode request, JsonNode evidencePolicy, String preparedResultId, JsonNode answers,
			boolean diagnosticOnly) {
		return new ClarifyBriefRequest(
				request, evidencePolicy, null, null, diagnosticOnly, preparedResultId, answers);
	}
}
