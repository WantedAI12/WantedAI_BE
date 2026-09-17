package com.perfumeryaicore.domain.prediction.service;

import com.perfumeryaicore.domain.formula.service.CandidateVersionRawView;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse.HumanValidation;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse.Simulation;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionUncertaintyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 조향 AI 응답 원문에서 예측·불확실성 관련 필드를 뽑아 {@link PredictionResponse}로 옮긴다.
 * 서브시스템별 세부 필드({@code physsim_*})는 개별 매핑 대신 원문 그대로 {@code diagnostics}에 모은다
 * — 필드가 많고(20개 이상) 상당수가 검증 실패로 가중치 0인 진단성 값이라 임의로 골라 단순화하면
 * 실제보다 확정적인 인상을 줄 위험이 있다.
 */
@Slf4j
@Component
public class PredictionMapper {

	private static final String DIAGNOSTIC_PREFIX = "physsim_";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public PredictionResponse toResponse(CandidateVersionRawView raw) {
		JsonNode root = parse(raw.rawResponse());
		if (root == null) {
			return new PredictionResponse(raw.candidateId(), raw.versionId(),
					null, null, null, null, null, null, null, null, null,
					emptyHumanValidation(), null, null, null);
		}

		HumanValidation humanValidation = new HumanValidation(
				bool(root, "human_similarity_90_claim_authorized"),
				number(root, "actual_olfactory_similarity_score"),
				number(root, "actual_olfactory_lower_bound_95"),
				number(root, "human_discrimination_probability"),
				number(root, "human_discrimination_lower_95"),
				number(root, "human_discrimination_upper_95"));

		Simulation simulation = new Simulation(
				text(root, "simulation_status"),
				number(root, "simulation_confidence"),
				descriptiveTextIfNotNumber(root, "simulation_confidence"),
				number(root, "simulation_p05"),
				number(root, "simulation_p95"),
				intVal(root, "simulation_draws"));

		return new PredictionResponse(
				raw.candidateId(),
				raw.versionId(),
				text(root, "status"),
				number(root, "similarity_score"),
				text(root, "similarity_kind"),
				text(root, "confidence"),
				number(root, "model_applicability_percent"),
				bool(root, "scientific_model_domain_passed"),
				text(root, "scientific_uncertainty_kind"),
				text(root, "olfactory_validation_status"),
				text(root, "perceptual_prediction_status"),
				humanValidation,
				node(root, "limitations"),
				simulation,
				diagnostics(root));
	}

	public PredictionUncertaintyResponse toUncertaintyResponse(CandidateVersionRawView raw) {
		PredictionResponse full = toResponse(raw);
		return new PredictionUncertaintyResponse(
				full.candidateId(), full.versionId(), full.modelApplicabilityPercent(),
				full.scientificModelDomainPassed(), full.scientificUncertaintyKind(),
				full.simulation(), full.diagnostics());
	}

	/** {@code physsim_} 접두 필드를 전부 모아 원문 그대로 반환한다(재해석 없음). */
	private JsonNode diagnostics(JsonNode root) {
		ObjectNode diagnostics = jsonMapper.createObjectNode();
		for (var entry : root.properties()) {
			if (entry.getKey().startsWith(DIAGNOSTIC_PREFIX)) {
				diagnostics.set(entry.getKey(), entry.getValue());
			}
		}
		return diagnostics.isEmpty() ? null : diagnostics;
	}

	private JsonNode parse(String rawResponse) {
		if (rawResponse == null) {
			return null;
		}
		try {
			return jsonMapper.readTree(rawResponse);
		} catch (JacksonException e) {
			log.warn("[PREDICTION] failed to parse stored raw response: {}", e.getMessage());
			return null;
		}
	}

	private static HumanValidation emptyHumanValidation() {
		return new HumanValidation(null, null, null, null, null, null);
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() ? null : value.asString();
	}

	private static Boolean bool(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() ? null : value.asBoolean();
	}

	/**
	 * AI팀 확인(2026-09-17): 배포 시점이 어긋난 과거 응답은 이 필드가 숫자가 아닌 설명 문자열
	 * (예: {@code "heuristic_only"})로 저장돼 있을 수 있다 - {@code isNumber()} 없이 바로
	 * {@code asDouble()}을 부르면 조회 시 오류가 난다. 숫자가 아니면 null이고, 그 문자열
	 * 자체는 {@link #descriptiveTextIfNotNumber}가 별도로 보존한다(0으로 대체하지 않음).
	 */
	private static Double number(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() || !value.isNumber() ? null : value.asDouble();
	}

	/** {@code field}가 숫자가 아닌 문자열일 때만 그 설명값을 그대로 반환한다(숫자면 null). */
	private static String descriptiveTextIfNotNumber(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull() || value.isNumber()) {
			return null;
		}
		return value.asString();
	}

	private static Integer intVal(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() || value.isNull() || !value.isIntegralNumber() ? null : value.asInt();
	}

	private static JsonNode node(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() ? null : value;
	}
}
