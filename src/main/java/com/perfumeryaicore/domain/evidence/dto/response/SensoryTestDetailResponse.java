package com.perfumeryaicore.domain.evidence.dto.response;

/**
 * @param predictedSimilarityScore 조회 시점 prediction 도메인의 similarityScore(§6). 저장값이 아니라
 *                                 매번 새로 읽은 값 — {@code results[].correlationWithPrediction}과
 *                                 나란히 비교해 보라고 곁들이는 참고값이며, 상관도를 대신 계산해주지 않는다.
 */
public record SensoryTestDetailResponse(
		SensoryTestResponse test,
		Double predictedSimilarityScore
) {
}
