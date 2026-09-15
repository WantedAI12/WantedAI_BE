package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/briefs/prepare} 응답. 최상위 판정 필드(status/questions/review_id 등)만
 * 이름 붙여 받고, 모델 내부 해석·계약 정보({@code prepared}/{@code reviewed_request}/
 * {@code contract})는 원문 노드 그대로 보존한다 - AI 개발자 문서 자체가 "관찰된 필드를 필수
 * 필드 전체로 단정하지 않는다"고 명시했다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrepareBriefResponse(

		@JsonProperty("schema_version")
		String schemaVersion,

		String status,

		@JsonProperty("missing_fields")
		List<String> missingFields,

		@JsonProperty("conflicting_fields")
		List<String> conflictingFields,

		List<PrepareQuestion> questions,

		JsonNode prepared,

		@JsonProperty("reviewed_request")
		JsonNode reviewedRequest,

		@JsonProperty("reviewed_lines")
		JsonNode reviewedLines,

		@JsonProperty("evidence_policy")
		JsonNode evidencePolicy,

		JsonNode contract,

		@JsonProperty("review_id")
		String reviewId,

		@JsonProperty("confirmation_required")
		Boolean confirmationRequired,

		@JsonProperty("recipe_generated")
		Boolean recipeGenerated,

		@JsonProperty("result_id")
		String resultId
) {

	/** {@code status=ready}: 보완 질문 없이 검토가 끝났다 - {@code reviewId}로 evaluate/reassess를 진행할 수 있다. */
	public boolean isReady() {
		return "ready".equals(status);
	}

	/** {@code status=needs_input}: {@code questions}에 답해 {@code /v2/briefs/clarify}로 다시 보내야 한다. */
	public boolean needsInput() {
		return "needs_input".equals(status);
	}
}
