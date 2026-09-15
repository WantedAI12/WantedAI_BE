package com.perfumeryaicore.domain.request.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.Map;

/**
 * v2 {@code /v2/briefs/clarify} 호출용. {@code preparedResultId}는 직전 prepare/clarify 응답의
 * {@code result_id}, {@code answers}는 질문 id → 답변값(문자열/숫자 등 혼재) 맵이다.
 */
public record BriefClarifyRequest(

		@NotBlank
		String preparedResultId,

		@NotEmpty
		Map<String, Object> answers,

		@Positive
		Double finishedBatchMassG,

		@Positive
		Integer maximumLeadTimeDays,

		@Positive
		Double maximumPurchaseCostUsd
) {
}
