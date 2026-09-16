package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/formulas/evaluate} 요청 본문. {@code confirmedReviewId}는
 * {@code /v2/briefs/prepare}(또는 clarify)가 {@code status=ready}일 때 돌려준 {@code review_id}를
 * 그대로 넣는다 - {@code diagnosticOnly=true}라도 이 값은 여전히 필수다(Modal 스키마).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluateFormulaRequest(

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

	/**
	 * 등록된 규제·공급 근거 없이도 시도할 수 있는 진단 전용 호출. 성공하더라도 정식 승인
	 * 후보가 아니다 - 호출부가 화면에 "진단 결과"로 명확히 구분해 표시해야 한다.
	 */
	public static EvaluateFormulaRequest diagnostic(JsonNode request, JsonNode evidencePolicy, String confirmedReviewId) {
		return new EvaluateFormulaRequest(request, evidencePolicy, null, null, true, confirmedReviewId);
	}
}
