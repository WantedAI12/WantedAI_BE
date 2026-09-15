package com.perfumeryaicore.domain.request.dto.request;

import com.perfumeryaicore.global.client.dto.EvidenceLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * 진단 전용 reassess 호출. {@link EvaluateDiagnosticRequest}와 같은 {@code confirmedReviewId}
 * 제약에 더해, 재평가할 고정 배합({@code lines})이 필요하다 - {@code concentratePercent}는
 * 완제품 함량이 아니라 향료 농축액 안의 배합 비율이다(AI 개발팀 확인).
 */
public record ReassessDiagnosticRequest(

		@NotBlank
		@Pattern(regexp = "^[0-9a-f]{64}$", message = "review_id는 64자 hex 문자열이어야 합니다")
		String confirmedReviewId,

		@NotEmpty
		List<EvidenceLine> lines,

		@Positive
		Double finishedBatchMassG,

		@Positive
		Integer maximumLeadTimeDays,

		@Positive
		Double maximumPurchaseCostUsd
) {
}
