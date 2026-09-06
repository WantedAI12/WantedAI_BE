package com.perfumeryaicore.domain.evidence.dto.request;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/**
 * @param resultData                패널·측정 결과. 구조를 강제하지 않는다(도구·프로토콜마다 다름)
 * @param correlationWithPrediction 감각과학 담당자가 직접 산출한 예측-실측 상관도. 백엔드가 계산하지 않는다
 */
public record SensoryTestResultCreateRequest(

		@NotNull
		JsonNode resultData,

		Double correlationWithPrediction
) {
}
