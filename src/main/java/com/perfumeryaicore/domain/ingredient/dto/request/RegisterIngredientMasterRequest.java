package com.perfumeryaicore.domain.ingredient.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RegisterIngredientMasterRequest(

		@NotBlank
		@Size(max = 100)
		String externalId,

		@Size(max = 20)
		String casNumber,

		@NotBlank
		@Size(max = 200)
		String name,

		List<@Size(max = 100) String> synonyms,

		@Size(max = 200)
		String supplierName,

		@Size(max = 4000)
		String safetyNotes,

		@Size(max = 4000)
		String regulatoryNotes
) {
}
