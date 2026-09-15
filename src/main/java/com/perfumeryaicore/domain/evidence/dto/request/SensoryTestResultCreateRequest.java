package com.perfumeryaicore.domain.evidence.dto.request;

import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * @param resultData                패널·측정 결과. 구조를 강제하지 않는다(도구·프로토콜마다 다름).
 *                                  측정값이 없으면(결측) {@code missingReason}을 함께 보내야 한다
 * @param correlationWithPrediction 감각과학 담당자가 직접 산출한 예측-실측 상관도. 백엔드가 계산하지 않는다
 * @param panelistIdentifier        평가자 익명 식별자(예: {@code P07}). 실제 회원 ID를 넣지 않는다
 * @param timepointMinutes          측정 시점(분)
 * @param scaleMin                  사용한 척도의 최솟값
 * @param scaleMax                  사용한 척도의 최댓값
 * @param missingReason             측정값이 없는 경우의 사유
 * @param supersedesResultId        이 제출이 이전 결과를 수정한 것이면 그 원본 결과 ID. 원본은 지우거나
 *                                  고치지 않고 그대로 남는다
 */
public record SensoryTestResultCreateRequest(

		JsonNode resultData,

		Double correlationWithPrediction,

		@Size(max = 50)
		String panelistIdentifier,

		Integer timepointMinutes,

		Double scaleMin,

		Double scaleMax,

		@Size(max = 1000)
		String missingReason,

		Long supersedesResultId
) {
}
