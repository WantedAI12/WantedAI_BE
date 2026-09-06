package com.perfumeryaicore.domain.formula.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 후보 버전 상세. 시간 변화 필드는 조향 AI 응답 원문을 그대로 다시 노출한 것이다(추정치이며
 * 실측·제조 승인이 아님 — {@code temporal.claimBoundary} 참고).
 */
public record CandidateVersionResponse(
		Long versionId,
		Long candidateId,
		Long parentVersionId,
		List<IngredientLine> ingredients,
		Double cost,
		String generationRationale,
		GenerationMeta generationMeta,
		Temporal temporal,
		Long createdBy,
		LocalDateTime createdAt
) {

	public record IngredientLine(
			String ingredientId,
			String name,
			String pyramid,
			Double concentratePercent,
			Double finishedProductPercent,
			Double pricePerKg,
			Double availability
	) {
	}

	/** 조향 AI(Modal) 응답 메타데이터. 근거(Evidence) 목적으로 보관한다. */
	public record GenerationMeta(
			String provider,
			Boolean gpuUsed,
			String aiResponseStatus,
			Long latencyMs
	) {
	}

	/**
	 * 시간에 따른 향·농도 변화. {@code timepointsMinutes}는 항상 {@code [0,15,60,240,480]}.
	 * 화면 문구는 "예상/추정"으로 표기하고 실측·후각 정확도·제조 승인으로 표현하지 않는다.
	 */
	public record Temporal(
			List<Integer> timepointsMinutes,
			List<JsonNode> profile,
			List<JsonNode> ingredientProfile,
			JsonNode concentrationBasis,
			String claimBoundary
	) {
	}
}
