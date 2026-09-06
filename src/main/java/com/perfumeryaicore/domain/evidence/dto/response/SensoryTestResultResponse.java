package com.perfumeryaicore.domain.evidence.dto.response;

import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

public record SensoryTestResultResponse(
		Long resultId,
		Long testId,
		JsonNode resultData,
		Double correlationWithPrediction,
		Long recordedBy,
		LocalDateTime recordedAt
) {
}
