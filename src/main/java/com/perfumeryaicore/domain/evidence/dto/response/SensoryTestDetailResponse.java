package com.perfumeryaicore.domain.evidence.dto.response;

/**
 * @param predictedSimilarityScore 관능 계획을 세운 시점에 고정한 prediction 도메인의
 *                                 similarityScore(§6, BE-057). 조회 시점의 "현재" 예측이 아니라
 *                                 계획 당시 저장한 값이다 — 후보가 이후 새 버전을 얻어도 이 관능
 *                                 결과가 실제로 검증한 배합의 예측값은 바뀌지 않는다.
 *                                 {@code results[].correlationWithPrediction}과 나란히 비교해 보라고
 *                                 곁들이는 참고값이며, 상관도를 대신 계산해주지 않는다.
 */
public record SensoryTestDetailResponse(
		SensoryTestResponse test,
		Double predictedSimilarityScore
) {
}
