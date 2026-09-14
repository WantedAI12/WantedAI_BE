package com.perfumeryaicore.domain.ingredient.dto.request;

import jakarta.validation.constraints.Size;
import java.util.List;

/** 부분 수정. {@code null}인 필드는 바뀌지 않는다. externalId는 여기서 바꿀 수 없다(신원 고정). */
public record UpdateIngredientMasterRequest(

		@Size(max = 20)
		String casNumber,

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
