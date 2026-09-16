package com.perfumeryaicore.domain.ingredient.dto.response;

import com.perfumeryaicore.domain.ingredient.entity.IngredientMaster;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 원료 마스터 한 건. {@code pricePerKg}/{@code profile}은 실시간 견적이 아니라 등록 시점에
 * 저장된 참고 스냅샷이다 - 후보 생성에서 실제 관측된 값은 {@link IngredientResponse}가 담당한다.
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
		String pyramid,
		JsonNode profile,
		Double pricePerKg,
		String priceCurrency,
		Integer riskTier,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static IngredientMasterResponse from(IngredientMaster entity, JsonMapper jsonMapper) {
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
				entity.getPyramid(),
				parseProfile(entity.getProfileJson(), jsonMapper),
				entity.getPricePerKg(),
				entity.getPriceCurrency(),
				entity.getRiskTier(),
				entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	private static JsonNode parseProfile(String profileJson, JsonMapper jsonMapper) {
		if (!StringUtils.hasText(profileJson)) {
			return null;
		}
		try {
			return jsonMapper.readTree(profileJson);
		} catch (JacksonException e) {
			return null;
		}
	}
}
