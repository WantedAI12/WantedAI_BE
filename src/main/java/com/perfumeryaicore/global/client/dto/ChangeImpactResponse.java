package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/formulas/change-impact} 응답. AI팀이 제공한 예시가 전부 근거 게이트
 * 거부/입력 오류(둘 다 이 계약과 같은 {@code status}/{@code reason}/{@code candidates} 모양)뿐이라,
 * 실제 성공(200) 응답의 전체 필드는 아직 확인되지 않았다(AI팀에 예시 요청함) - 모르는 필드는
 * {@code @JsonIgnoreProperties}로 무시하고, 확인된 것만 타입으로 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangeImpactResponse(

		@JsonProperty("schema_version")
		String schemaVersion,

		String status,

		String reason,

		List<JsonNode> candidates,

		@JsonProperty("result_id")
		String resultId
) {
}
