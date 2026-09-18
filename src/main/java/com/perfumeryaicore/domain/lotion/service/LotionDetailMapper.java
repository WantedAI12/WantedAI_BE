package com.perfumeryaicore.domain.lotion.service;

import com.perfumeryaicore.domain.formula.service.CandidateVersionRawView;
import com.perfumeryaicore.domain.lotion.dto.response.LotionDetailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 저장된 바디로션 응답 원문에서 상세 조회에 필요한 값을 뽑는다. 최상위 스칼라 필드만 개별
 * 매핑하고, 깊게 중첩된 하위 구조는 원문 그대로 넘긴다({@link LotionDetailResponse} 참고).
 */
@Slf4j
@Component
public class LotionDetailMapper {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public LotionDetailResponse toResponse(CandidateVersionRawView raw) {
		JsonNode root = parse(raw.rawResponse());
		if (root == null) {
			return LotionDetailResponse.empty(raw.candidateId(), raw.versionId());
		}

		return new LotionDetailResponse(
				raw.candidateId(),
				raw.versionId(),
				text(root, "status"),
				bool(root, "profile_target_met"),
				bool(root, "search_incomplete"),
				text(root, "candidate_use"),
				text(root, "score_kind"),
				node(root, "score"),
				bool(root, "manufacturing_approved"),
				bool(root, "all_user_requirements_verified"),
				number(root, "human_similarity_percent"),
				node(root, "product_model"),
				node(root, "preparation"),
				node(root, "perception_model"),
				node(root, "recipe"),
				node(root, "closest_candidate"),
				text(root.path("perfumer_notes"), "text"),
				node(root, "candidate_explanation"),
				node(root, "safety_explanation"));
	}

	private JsonNode parse(String rawResponse) {
		if (rawResponse == null) {
			return null;
		}
		try {
			return jsonMapper.readTree(rawResponse);
		} catch (JacksonException e) {
			log.warn("[LOTION] failed to parse stored raw response: {}", e.getMessage());
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
		return value.isMissingNode() || value.isNull() || !value.isNumber() ? null : value.asDouble();
	}

	private static JsonNode node(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isMissingNode() ? null : value;
	}
}
