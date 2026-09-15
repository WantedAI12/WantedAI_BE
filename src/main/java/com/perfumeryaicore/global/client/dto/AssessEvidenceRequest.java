package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Modal {@code POST /v2/formulas/assess-evidence} 요청 본문. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssessEvidenceRequest(

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

		JsonNode policy
) {
}
