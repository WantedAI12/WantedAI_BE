package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/briefs/prepare} 요청 본문. {@code request}/{@code evidence_policy}만
 * 필수이고 나머지는 Modal 스키마상 선택이라 지금은 쓰지 않는다({@code lines}/{@code revision}/
 * {@code diagnostic_only}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PrepareBriefRequest(

		@JsonProperty("request")
		JsonNode request,

		@JsonProperty("evidence_policy")
		JsonNode evidencePolicy,

		@JsonProperty("lines")
		JsonNode lines,

		@JsonProperty("revision")
		Integer revision,

		@JsonProperty("diagnostic_only")
		Boolean diagnosticOnly
) {

	public static PrepareBriefRequest of(JsonNode request, JsonNode evidencePolicy) {
		return new PrepareBriefRequest(request, evidencePolicy, null, null, null);
	}
}
