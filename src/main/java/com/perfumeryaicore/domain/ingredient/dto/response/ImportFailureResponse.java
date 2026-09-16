package com.perfumeryaicore.domain.ingredient.dto.response;

import com.perfumeryaicore.domain.ingredient.entity.IngredientImportFailure;
import java.time.LocalDateTime;

public record ImportFailureResponse(
		Long id,
		String externalId,
		String errorMessage,
		boolean resolved,
		LocalDateTime createdAt
) {

	public static ImportFailureResponse from(IngredientImportFailure f) {
		return new ImportFailureResponse(
				f.getId(), f.getExternalId(), f.getErrorMessage(), f.isResolved(), f.getCreatedAt());
	}
}
