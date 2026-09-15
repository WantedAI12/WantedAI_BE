package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code GET /v1/ai/capabilities} 응답. 런타임/기능 정보 확인용({@code /health}와 함께
 * BE가 저장하지 않는 버전 정보의 출처). 모델별 세부 계약(perception_model 등)은 원문 노드로
 * 보존한다 - 필드 수가 많고 자주 바뀔 수 있어 개별 타입화는 하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiCapabilitiesResponse(

		@JsonProperty("schema_version")
		String schemaVersion,

		@JsonProperty("supported_product_codes")
		List<String> supportedProductCodes,

		@JsonProperty("physical_property_evidence")
		JsonNode physicalPropertyEvidence,

		@JsonProperty("integration_contract")
		JsonNode integrationContract,

		@JsonProperty("language_model")
		JsonNode languageModel,

		@JsonProperty("perception_model")
		JsonNode perceptionModel,

		@JsonProperty("stock_mixture_model")
		JsonNode stockMixtureModel,

		@JsonProperty("unified_product_model")
		JsonNode unifiedProductModel,

		@JsonProperty("odor_expression")
		JsonNode odorExpression,

		@JsonProperty("formulation_knowledge")
		JsonNode formulationKnowledge,

		@JsonProperty("shared_formulation_model")
		JsonNode sharedFormulationModel,

		@JsonProperty("product_models")
		JsonNode productModels,

		JsonNode features,

		JsonNode limits,

		String scope
) {
}
