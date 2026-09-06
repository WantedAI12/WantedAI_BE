package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.GenerationMeta;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.IngredientLine;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.Temporal;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link CandidateVersion} + 원료 목록을 응답 DTO로 변환한다. 시간 변화 필드는 저장된
 * {@code rawResponse} 원문을 다시 파싱해 채운다(저장 시 별도 컬럼으로 뽑아두지 않았기 때문).
 */
@Slf4j
@Component
public class CandidateVersionMapper {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public CandidateVersionResponse toResponse(CandidateVersion version, List<CandidateVersionIngredient> ingredients) {
		List<IngredientLine> lines = ingredients.stream()
				.map(i -> new IngredientLine(
						i.getIngredientExternalId(),
						i.getIngredientName(),
						i.getPyramid(),
						i.getConcentratePercent(),
						i.getFinishedProductPercent(),
						i.getPricePerKg(),
						i.getAvailability()))
				.toList();

		FormulaGenerationResponse parsed = tryParse(version.getRawResponse());
		Temporal temporal = parsed == null ? null : new Temporal(
				parsed.temporalTimepointsMinutes(),
				parsed.temporalProfile(),
				parsed.ingredientTemporalProfile(),
				parsed.temporalConcentrationBasis(),
				parsed.temporalModelClaimBoundary());

		GenerationMeta meta = new GenerationMeta(
				version.getAiProvider(),
				version.getAiGpuUsed(),
				version.getAiResponseStatus(),
				version.getAiLatencyMs());

		return new CandidateVersionResponse(
				version.getId(),
				version.getCandidateId(),
				version.getParentVersionId(),
				lines,
				version.getCost(),
				version.getGenerationRationale(),
				meta,
				temporal,
				version.getCreatedBy(),
				version.getCreatedAt());
	}

	private FormulaGenerationResponse tryParse(String rawResponse) {
		if (rawResponse == null) {
			return null;
		}
		try {
			return jsonMapper.readValue(rawResponse, FormulaGenerationResponse.class);
		} catch (JacksonException e) {
			log.warn("[FORMULA] failed to re-parse stored raw response: {}", e.getMessage());
			return null;
		}
	}
}
