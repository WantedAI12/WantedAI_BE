package com.perfumeryaicore.domain.request.dto.response;

/**
 * BE-097: 프로젝트 단위 진행률 = 소속 작업들의 완료 항목 수 합 ÷ 전체 항목 수 합 × 100
 * (작업별 진행률의 단순 평균이 아니다 - 항목 수가 다른 작업들을 항목 수로 가중해 집계한다).
 */
public record ProjectProgressResponse(
		Long projectId,
		int workCount,
		int completedCount,
		int totalCount,
		double percent
) {
}
