package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v2/formulas/change-impact} 요청 본문. {@link AssessEvidenceRequest}와 같은
 * 배합·정책 필드에 {@code previousEvidenceVersion}만 추가된 형태다(V80 연동자료, 2026-09-15).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChangeImpactRequest(

		List<EvidenceLine> lines,

		@JsonProperty("target_region")
		String targetRegion,

		@JsonProperty("product_category")
		String productCategory,

		@JsonProperty("product_concentration_percent")
		Double productConcentrationPercent,

		@JsonProperty("max_formula_cost_per_kg")
		Double maxFormulaCostPerKg,

		@JsonProperty("max_ingredient_price_per_kg")
		Double maxIngredientPricePerKg,

		JsonNode policy,

		@JsonProperty("previous_evidence_version")
		String previousEvidenceVersion
) {
}
