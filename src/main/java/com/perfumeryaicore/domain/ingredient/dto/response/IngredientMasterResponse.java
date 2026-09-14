package com.perfumeryaicore.domain.ingredient.dto.response;

import com.perfumeryaicore.domain.ingredient.entity.IngredientMaster;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * 원료 마스터 한 건. 가격·가용성 관측값은 여기 없다 - 그건
 * {@link IngredientResponse}(후보에서 관측된 값)가 담당한다.
 */
public record IngredientMasterResponse(
		Long id,
		String externalId,
		String casNumber,
		String name,
		List<String> synonyms,
		String supplierName,
		String safetyNotes,
		String regulatoryNotes,
		Long registeredBy,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static IngredientMasterResponse from(IngredientMaster entity) {
		List<String> synonyms = StringUtils.hasText(entity.getSynonymsCsv())
				? Arrays.stream(entity.getSynonymsCsv().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList()
				: List.of();
		return new IngredientMasterResponse(
				entity.getId(),
				entity.getExternalId(),
				entity.getCasNumber(),
				entity.getName(),
				synonyms,
				entity.getSupplierName(),
				entity.getSafetyNotes(),
				entity.getRegulatoryNotes(),
				entity.getRegisteredBy(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}
}
