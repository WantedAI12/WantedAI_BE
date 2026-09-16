package com.perfumeryaicore.domain.request.dto.request;

import com.perfumeryaicore.global.client.dto.EvidenceLine;
import jakarta.validation.constraints.Positive;
import java.util.List;

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
		Double maximumPurchaseCostUsd,

		/**
		 * 고정 배합을 리뷰하는 경우에만 채운다({@code /v2/formulas/reassess} 진행 순서 1단계,
		 * AI 개발팀 확인) - 여기서 지정한 배합과 다른 lines로 reassess를 호출하면 안 된다.
		 */
		List<EvidenceLine> lines,

		/**
		 * AI 개발팀 확인(2026-09-16): 진단 모드는 prepare 단계부터 지정해야 한다 - 여기서 받은
		 * review_id만 진단 모드 evaluate/reassess에 재사용할 수 있다(모드가 다르면 409).
		 */
		Boolean diagnosticOnly
) {

	public static BriefReviewRequest empty() {
		return new BriefReviewRequest(null, null, null, null, null);
	}

	public boolean isDiagnosticOnly() {
		return Boolean.TRUE.equals(diagnosticOnly);
	}
}
