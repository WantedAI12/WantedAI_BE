package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code GET /v2/evidence/coverage} 응답. 원료별 공개 자료 연결 범위(offset/limit 페이지네이션,
 * limit 최대 500). {@code items} 각 원소는 원료별 규제 목록 매칭 정보까지 포함해 깊고 가변적이라
 * 원문 노드 그대로 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvidenceCoverageResponse(

		@JsonProperty("active_material_count")
		Integer activeMaterialCount,

		@JsonProperty("public_price_connected_count")
		Integer publicPriceConnectedCount,

		@JsonProperty("no_public_price_count")
		Integer noPublicPriceCount,

		@JsonProperty("operationally_complete_count")
		Integer operationallyCompleteCount,

		@JsonProperty("source_products_examined")
		Integer sourceProductsExamined,

		String scope,

		Integer offset,

		Integer limit,

		List<JsonNode> items,

		@JsonProperty("has_more")
		Boolean hasMore
) {
}
