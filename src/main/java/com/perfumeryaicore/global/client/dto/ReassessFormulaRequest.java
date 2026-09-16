package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/formulas/reassess} 요청 본문. {@code evaluate}와 달리 {@code lines}(고정
 * 배합)가 필수다 - 이미 정해진 배합을 그대로 두고 조건만 재평가한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReassessFormulaRequest(

		JsonNode request,

		@JsonProperty("evidence_policy")
		JsonNode evidencePolicy,

		List<EvidenceLine> lines,

		JsonNode revision,

		@JsonProperty("diagnostic_only")
		Boolean diagnosticOnly,

		@JsonProperty("confirmed_review_id")
		String confirmedReviewId
) {

	/** {@link EvaluateFormulaRequest#diagnostic}와 같은 이유로, 진단 결과임을 호출부가 구분해야 한다. */
	public static ReassessFormulaRequest diagnostic(
			JsonNode request, JsonNode evidencePolicy, List<EvidenceLine> lines, String confirmedReviewId) {
		return new ReassessFormulaRequest(request, evidencePolicy, lines, null, true, confirmedReviewId);
	}
}
