package com.perfumeryaicore.domain.ingredient.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** BE-063~066: 원료 마스터 대량 등록. 행 하나가 실패해도 나머지는 등록되고, 실패한 행은 재처리 큐에 남는다. */
public record BulkImportIngredientMastersRequest(

		@NotEmpty
		@Size(max = 500)
		List<@Valid RegisterIngredientMasterRequest> items
) {
}
