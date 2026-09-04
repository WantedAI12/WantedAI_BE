package com.perfumeryaicore.domain.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.experiment.dto.response.CandidateCompareRow;
import com.perfumeryaicore.domain.experiment.service.CandidateCompareService;
import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.IngredientLine;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse.HumanValidation;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateCompareServiceTest {

	private final CandidateService candidateService = mock(CandidateService.class);
	private final PredictionService predictionService = mock(PredictionService.class);
	private final CandidateCompareService service = new CandidateCompareService(candidateService, predictionService);

	private CandidateResponse candidate(Long candidateId, Long requestId, Double cost, Double... availabilities) {
		List<IngredientLine> ingredients = java.util.Arrays.stream(availabilities)
				.map(a -> new IngredientLine("ing", "Ingredient", "top", 10.0, 1.0, 20.0, a))
				.toList();
		CandidateVersionResponse version = new CandidateVersionResponse(
				900L, candidateId, null, ingredients, cost, "rationale", null, null, LocalDateTime.now());
		return new CandidateResponse(candidateId, requestId, CandidateStatus.UNDER_REVIEW, version);
	}

	private PredictionResponse prediction(Long candidateId, Double similarity, Double applicability) {
		return new PredictionResponse(candidateId, 900L, "prototype_ready", similarity, "kind", 0.5,
				applicability, true, "kind", "status", "status",
				new HumanValidation(false, null, null, null, null, null),
				null, null, null);
	}

	@Test
	void builds_compare_row_from_formula_and_prediction_data() {
		when(candidateService.get(1L, 1L)).thenReturn(candidate(1L, 5L, 42.0, 0.9, 0.7));
		when(predictionService.get(1L, 1L)).thenReturn(prediction(1L, 87.5, 64.0));

		List<CandidateCompareRow> rows = service.compare(5L, List.of(1L), 1L);

		assertThat(rows).hasSize(1);
		CandidateCompareRow row = rows.get(0);
		assertThat(row.candidateId()).isEqualTo(1L);
		assertThat(row.cost()).isEqualTo(42.0);
		assertThat(row.goalMatchScore()).isEqualTo(87.5);
		assertThat(row.modelApplicabilityPercent()).isEqualTo(64.0);
		assertThat(row.supplyStability()).isEqualTo(80.0); // (0.9+0.7)/2 * 100
	}

	@Test
	void candidate_from_a_different_request_is_rejected() {
		when(candidateService.get(1L, 1L)).thenReturn(candidate(1L, 999L, 42.0, 0.9));

		assertThatThrownBy(() -> service.compare(5L, List.of(1L), 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_NOT_FOUND);
	}

	@Test
	void missing_current_version_yields_null_cost_and_stability() {
		CandidateResponse noVersion = new CandidateResponse(1L, 5L, CandidateStatus.UNDER_REVIEW, null);
		when(candidateService.get(1L, 1L)).thenReturn(noVersion);
		when(predictionService.get(1L, 1L)).thenReturn(prediction(1L, null, null));

		CandidateCompareRow row = service.compare(5L, List.of(1L), 1L).get(0);

		assertThat(row.cost()).isNull();
		assertThat(row.supplyStability()).isNull();
	}
}
