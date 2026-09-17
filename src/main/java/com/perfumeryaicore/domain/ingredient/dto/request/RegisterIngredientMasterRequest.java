package com.perfumeryaicore.domain.ingredient.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record RegisterIngredientMasterRequest(

		@NotBlank
		@Size(max = 100)
		String externalId,

		@Size(max = 20)
		String casNumber,

		@NotBlank
		@Size(max = 200)
		String name,

		List<@Size(max = 150) String> synonyms,

		@Size(max = 200)
		String supplierName,

		@Size(max = 4000)
		String safetyNotes,

		@Size(max = 4000)
		String regulatoryNotes,

		/** top/heart/base 등 원문 그대로 - 없는 값을 추정해 넣지 않는다. */
		@Size(max = 20)
		String pyramid,

		/** 향 표현별 가중치 객체(예: {"citrus":0.7,"fresh":1.0}). 원문 그대로 보존한다. */
		JsonNode profile,

		Double pricePerKg,

		/** 통화·추정 여부(예: "USD_estimate_not_supplier_quote"). 가격과 항상 같이 저장한다. */
		@Size(max = 50)
		String priceCurrency,

		Integer riskTier
) {
}
