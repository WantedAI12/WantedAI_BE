package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/formulas/change-impact} 성공(200) 응답. AI팀이 V89-backend-contract-rc01
 * 기준 실제 200 응답 원문으로 확인해 준 계약이다(이전엔 422 예시만 있어 status/reason/candidates로
 * 잘못 추정했었음). 등록된 근거가 없어 422로 거부되는 경우는
 * {@link com.perfumeryaicore.global.client.PerfumeryAiClient}의 공통 오류 처리 경로로 빠지고
 * 이 타입으로 파싱되지 않는다.
 *
 * <p>{@code before}/{@code after}/{@code changes}/{@code contract}의 내부 구조는 아직 세부 계약을
 * 받지 않아 원문 노드로 보존한다. 이 응답은 공개자료 버전 간 비교이지 운영자 승인 근거 비교와는
 * 내부 구조가 다르므로({@code scope}로 구분) 운영용 공급·규제 근거 충족을 뜻하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangeImpactResponse(

		@JsonProperty("schema_version")
		String schemaVersion,

		JsonNode before,

		JsonNode after,

		JsonNode changes,

		@JsonProperty("affected_material_count")
		Integer affectedMaterialCount,

		@JsonProperty("review_required")
		Boolean reviewRequired,

		@JsonProperty("state_changed")
		Boolean stateChanged,

		@JsonProperty("manufacturing_approval")
		Boolean manufacturingApproval,

		String scope,

		@JsonProperty("result_id")
		String resultId,

		JsonNode contract
) {
}
