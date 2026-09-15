package com.perfumeryaicore.domain.request.dto.request;

import jakarta.validation.constraints.Positive;

/**
 * v2 {@code /v2/briefs/prepare} 호출용 근거 정책(evidence_policy). 저장된 요청 필드만으로는
 * 알 수 없는 값이라 별도로 받는다 - 전부 선택값이며, 비우면 Modal 기본값이 적용된다.
 */
public record BriefReviewRequest(

		@Positive
		Double finishedBatchMassG,

		@Positive
		Integer maximumLeadTimeDays,

		@Positive
		Double maximumPurchaseCostUsd
) {

	public static BriefReviewRequest empty() {
		return new BriefReviewRequest(null, null, null);
	}
}
