package com.perfumeryaicore.domain.safety.dto.response;

import tools.jackson.databind.JsonNode;

/**
 * 후보의 안전·규제·공급 적합성 평가 결과. 새 AI 호출이 아니라 후보 생성 시 저장된 조향 AI 응답의
 * {@code safety} 구간을 다시 읽어 보여준다(§0.5). 구조가 불확실한 목록형 필드(위반·경고·미비 서류 등)는
 * 임의로 재해석하지 않고 조향 AI 응답 원문 그대로 노출한다.
 */
public record SafetyEvaluationResponse(
		Long candidateId,
		Long versionId,
		String status,
		Boolean internalGatePassed,
		Boolean manufacturingReady,
		String validationLevel,
		Double evidenceCoveragePercent,
		Boolean regulatoryDataComplete,
		Boolean internalEvidenceComplete,
		Boolean allergenQuantificationComplete,
		String targetRegion,
		String productCategory,
		String auditId,
		String standardsCheckedOn,
		String standardsReviewDue,
		JsonNode violations,
		JsonNode warnings,
		JsonNode missingDocuments,
		JsonNode potentialEuAllergens
) {
}
