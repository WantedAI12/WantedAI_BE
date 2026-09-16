package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/briefs/prepare} 요청 본문. {@code request}/{@code evidence_policy}만
 * 필수이고 {@code lines}/{@code revision}은 지금은 쓰지 않는다.
 *
 * <p>AI 개발팀 확인(2026-09-16): {@code diagnostic_only}는 evaluate/reassess 호출 시점이 아니라
 * 이 prepare 단계부터 최상위에 넣어야 한다 - 진단 모드로 받은 {@code review_id}만 진단 모드
 * evaluate/reassess에 재사용할 수 있고, 모드가 다른 review_id를 재사용하면 409가 난다.
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

	public static PrepareBriefRequest of(JsonNode request, JsonNode evidencePolicy, boolean diagnosticOnly) {
		return new PrepareBriefRequest(request, evidencePolicy, null, null, diagnosticOnly);
	}

	/** 고정 배합을 리뷰하는 reassess 진행 순서 1단계용 - {@code lines}는 이후 reassess에 넣을 것과 같아야 한다. */
	public static PrepareBriefRequest of(
			JsonNode request, JsonNode evidencePolicy, JsonNode lines, boolean diagnosticOnly) {
		return new PrepareBriefRequest(request, evidencePolicy, lines, null, diagnosticOnly);
	}
}
