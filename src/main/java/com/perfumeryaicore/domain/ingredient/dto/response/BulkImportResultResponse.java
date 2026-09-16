package com.perfumeryaicore.domain.ingredient.dto.response;

import java.util.List;

public record BulkImportResultResponse(
		int totalCount,
		int succeededCount,
		int failedCount,
		List<IngredientMasterResponse> registered,
		List<ImportFailureResponse> failures
) {
}
