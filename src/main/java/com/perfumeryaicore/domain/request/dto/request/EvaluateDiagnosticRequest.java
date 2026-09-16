package com.perfumeryaicore.domain.request.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 진단 전용 evaluate 호출(AI 개발팀 확인, 2026-09-16). {@code confirmedReviewId}는 반드시
 * {@code POST /requests/{requestId}/brief-review}를 {@code diagnosticOnly=true}로 호출해 받은
 * {@code review_id}여야 한다.
 */
public record EvaluateDiagnosticRequest(

		@NotBlank
		@Pattern(regexp = "^[0-9a-f]{64}$", message = "review_id는 64자 hex 문자열이어야 합니다")
		String confirmedReviewId,

		@Positive
		Double finishedBatchMassG,

		@Positive
		Integer maximumLeadTimeDays,

		@Positive
		Double maximumPurchaseCostUsd
) {
}
