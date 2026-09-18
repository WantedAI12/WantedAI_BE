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

		/**
		 * AI팀 확인(2026-09-18): 원료 배합비 목록만 보여주던 화면 대신 쓸 수 있는 조향사용
		 * 서술형 설명(콘셉트·원료별 배합 의도·향의 전개·검토 의견·다음 시향 확인 사항을 하나로
		 * 엮은 글, {@code perfumer_notes.text}). 아직 이 필드가 없던 과거 응답이면 {@code null}.
		 */
		String perfumerNotes,

		Long createdBy,
		LocalDateTime createdAt,

		/** BE-026: 이 버전이 과거 버전을 복원해 만들어졌다면 그 원본 버전 ID. 아니면 {@code null}. */
		Long restoredFromVersionId
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
