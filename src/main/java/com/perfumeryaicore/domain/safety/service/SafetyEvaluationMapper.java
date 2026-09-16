package com.perfumeryaicore.domain.safety.service;

import com.perfumeryaicore.domain.formula.service.CandidateVersionRawView;
import com.perfumeryaicore.domain.safety.dto.response.SafetyEvaluationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 조향 AI 응답 원문에서 {@code safety} 구간만 뽑아 {@link SafetyEvaluationResponse}로 옮긴다.
 * 원문을 재해석·보정하지 않는다 — 값이 없으면 {@code null}, 구조가 불확실한 목록은 원문 그대로.
 */
@Slf4j
@Component
public class SafetyEvaluationMapper {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public SafetyEvaluationResponse toResponse(CandidateVersionRawView raw) {
		JsonNode safety = safetyNode(raw.rawResponse());
		if (safety == null) {
			return new SafetyEvaluationResponse(raw.candidateId(), raw.versionId(),
					null, null, null, null, null, null, null, null, null, null, null, null, null,
					null, null, null, null);
		}
		return new SafetyEvaluationResponse(
				raw.candidateId(),
				raw.versionId(),
				text(safety, "status"),
				bool(safety, "internal_gate_passed"),
				bool(safety, "manufacturing_ready"),
				text(safety, "validation_level"),
				number(safety, "evidence_coverage_percent"),
				bool(safety, "regulatory_data_complete"),
				bool(safety, "internal_evidence_complete"),
				bool(safety, "allergen_quantification_complete"),
				text(safety, "target_region"),
				text(safety, "product_category"),
				text(safety, "audit_id"),
				text(safety, "standards_checked_on"),
				text(safety, "standards_review_due"),
				node(safety, "violations"),
				node(safety, "warnings"),
				node(safety, "missing_documents"),
				node(safety, "potential_eu_allergens"));
	}

	private JsonNode safetyNode(String rawResponse) {
		if (rawResponse == null) {
			return null;
		}
		try {
			JsonNode safety = jsonMapper.readTree(rawResponse).path("safety");
			return safety.isMissingNode() ? null : safety;
		} catch (JacksonException e) {
			log.warn("[SAFETY] failed to parse stored raw response: {}", e.getMessage());
			return null;
		}
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() ? null : value.asString();
	}

	private static Boolean bool(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() ? null : value.asBoolean();
	}

	private static Double number(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() ? null : value.asDouble();
	}

	private static JsonNode node(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() ? null : value;
	}
}
