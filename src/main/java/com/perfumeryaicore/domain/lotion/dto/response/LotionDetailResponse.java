package com.perfumeryaicore.domain.lotion.dto.response;

import tools.jackson.databind.JsonNode;

/**
 * 바디로션 후보 상세. 새 AI 호출이 아니라 저장된 조향 AI 응답을 다시 읽어 보여준다(§0.5).
 *
 * <p>{@code productModel}/{@code preparation}/{@code perceptionModel}/{@code recipe}/
 * {@code closestCandidate}는 원문 그대로(재해석 없이) 노출한다 - 실제 구조가 깊게 중첩되어
 * 있고(예: {@code limitations}는 {@code perceptionModel} 하위) 아직 안정적으로 확정되지
 * 않아, leaf 필드까지 개별 Java 필드로 강제하면 {@code confidence} 필드 버그와 같은 파싱
 * 실패 위험이 있다(formula 도메인의 {@code PredictionResponse.diagnostics}와 같은 패턴).
 */
public record LotionDetailResponse(
		Long candidateId,
		Long versionId,

		// 결과 판정
		String status,
		Boolean profileTargetMet,
		Boolean searchIncomplete,
		String candidateUse,

		// 점수 해석
		String scoreKind,
		JsonNode score,

		// 검토 한계
		Boolean manufacturingApproved,
		Boolean allUserRequirementsVerified,
		Double humanSimilarityPercent,

		// 상세 화면 · 배합 · 재현 (원문 그대로)
		JsonNode productModel,
		JsonNode preparation,
		JsonNode perceptionModel,
		JsonNode recipe,
		JsonNode closestCandidate,

		/**
		 * AI팀 확인(2026-09-18): 원료 배합비 목록만 보여주던 화면 대신 쓸 수 있는 조향사용
		 * 서술형 설명({@code perfumer_notes.text}). 아직 이 필드가 없던 과거 응답이면 {@code null}.
		 */
		String perfumerNotes
) {

	public static LotionDetailResponse empty(Long candidateId, Long versionId) {
		return new LotionDetailResponse(candidateId, versionId,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
	}
}
