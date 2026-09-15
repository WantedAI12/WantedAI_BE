package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code /v2/formulas/assess-evidence}·{@code /change-impact} 요청의 배합 한 줄. */
public record EvidenceLine(

		@JsonProperty("ingredient_id")
		String ingredientId,

		@JsonProperty("concentrate_percent")
		Double concentratePercent
) {
}
