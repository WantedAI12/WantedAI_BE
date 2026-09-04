package com.perfumeryaicore.domain.prediction.dto.response;

import tools.jackson.databind.JsonNode;

/**
 * {@link PredictionResponse}에서 적용범위·불확실성 진단만 추린 뷰. 유사도·인간 검증 관점은
 * {@code /predictions}에서 확인한다.
 */
public record PredictionUncertaintyResponse(
		Long candidateId,
		Long versionId,
		Double modelApplicabilityPercent,
		Boolean scientificModelDomainPassed,
		String scientificUncertaintyKind,
		PredictionResponse.Simulation simulation,
		JsonNode diagnostics
) {
}
