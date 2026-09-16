package com.perfumeryaicore.domain.evidence.dto.response;

import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

public record SensoryTestResultResponse(
		Long resultId,
		Long testId,
		JsonNode resultData,
		Double correlationWithPrediction,
		String panelistIdentifier,
		Integer timepointMinutes,
		Double scaleMin,
		Double scaleMax,
		String missingReason,
		Long supersedesResultId,
		Long recordedBy,
		LocalDateTime recordedAt
) {
}
