package com.perfumeryaicore.domain.request.dto.response;

/**
 * BE-097: 향수 작업(하나의 {@code FragranceRequest}) 단위 체크리스트 진행률.
 * {@code percent}는 100.0이 아닌 이상 99.9를 넘지 않는다(반올림으로 미완료 상태가 100%로
 * 보이는 것을 막는 규칙 - 고정 상한이 아니라 반올림 가드다).
 */
public record WorkProgressResponse(
		Long requestId,
		int completedCount,
		int totalCount,
		double percent
) {
}
