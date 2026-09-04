package com.perfumeryaicore.domain.prediction.dto.response;

import tools.jackson.databind.JsonNode;

/**
 * 후보의 성능 프록시 예측 결과. 새 AI 호출이 아니라 저장된 조향 AI 응답을 다시 읽어 보여준다(§0.5).
 *
 * <p>필드명은 조향 AI가 실제로 반환하는 이름을 그대로 따른다 — PRD 초안의 {@code uncertainty}/
 * {@code isOutOfDistribution}/{@code abstained} 같은 단일 통합 지표는 실제 응답에 존재하지 않는다.
 * 조향 AI의 과학 모델 문서(SCIENTIFIC_MODEL.md)에 따르면 이 값들은 여러 서브시스템(물성 프록시,
 * 농도 반응, R2 PhysSim 등)이 각자 적용범위·게이트 통과 여부를 보고하는 구조이고, 다수 가지가
 * 검증 실패 시 가중치 {@code 0}으로 비활성화된 채로 진단만 남긴다. 이 시스템에서 안전하지 않거나
 * 데이터 범위를 벗어난 요청은 이미 생성 단계(§4, {@code no_safe_match} → {@code GENERATION_REJECTED})에서
 * 걸러지므로, 저장된 후보에 대한 "기권" 개념은 별도로 존재하지 않는다.
 *
 * <p>{@code diagnostics}에는 서브시스템별 세부 필드(예: {@code physsim_*} 접두 필드)를
 * 재해석 없이 원문 그대로 모아 둔다.
 */
public record PredictionResponse(
		Long candidateId,
		Long versionId,
		String status,
		Double similarityScore,
		String similarityKind,
		Double confidence,
		Double modelApplicabilityPercent,
		Boolean scientificModelDomainPassed,
		String scientificUncertaintyKind,
		String olfactoryValidationStatus,
		String perceptualPredictionStatus,
		HumanValidation humanValidation,
		JsonNode limitations,
		Simulation simulation,
		JsonNode diagnostics
) {

	/**
	 * 독립적이고 서명된 인간 관능 결과에만 근거하는 값들. {@code similarity90ClaimAuthorized}가
	 * {@code true}가 아니면 "인간 후각 90% 재현" 같은 주장을 화면에 노출하지 않는다.
	 */
	public record HumanValidation(
			Boolean similarity90ClaimAuthorized,
			Double actualOlfactorySimilarityScore,
			Double actualOlfactoryLowerBound95,
			Double discriminationProbability,
			Double discriminationLowerBound95,
			Double discriminationUpperBound95
	) {
	}

	public record Simulation(
			String status,
			Double confidence,
			Double p05,
			Double p95,
			Integer draws
	) {
	}
}
