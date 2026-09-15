package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * {@code /v2/briefs/prepare}, {@code /v2/briefs/clarify}가 반환하는 보완 질문 한 건.
 * {@code options}은 {@code input_type}(choice/number 등)에 따라 필드 구성이 달라 원문 노드
 * 그대로 받는다 - 단정하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrepareQuestion(

		String id,

		String field,

		Boolean required,

		@JsonProperty("input_type")
		String inputType,

		String unit,

		List<JsonNode> options,

		@JsonProperty("reason_code")
		String reasonCode,

		String question
) {
}
