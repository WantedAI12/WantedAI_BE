package com.perfumeryaicore.domain.prediction;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.formula.service.CandidateVersionRawView;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionUncertaintyResponse;
import com.perfumeryaicore.domain.prediction.service.PredictionMapper;
import org.junit.jupiter.api.Test;

/** 2026-09-02 Modal 라이브 검증에서 관찰한 실제 최상위 필드 이름을 기준으로 검증한다. */
class PredictionMapperTest {

	private final PredictionMapper mapper = new PredictionMapper();

	private static final String RAW_RESPONSE = """
			{
			  "status": "prototype_ready",
			  "similarity_score": 87.42,
			  "similarity_kind": "semantic_profile_proxy",
			  "confidence": 0.71,
			  "model_applicability_percent": 64.0,
			  "scientific_model_domain_passed": true,
			  "scientific_uncertainty_kind": "monte_carlo_quantile",
			  "olfactory_validation_status": "not_independently_validated",
			  "perceptual_prediction_status": "research_only",
			  "human_similarity_90_claim_authorized": false,
			  "actual_olfactory_similarity_score": null,
			  "actual_olfactory_lower_bound_95": null,
			  "human_discrimination_probability": null,
			  "human_discrimination_lower_95": null,
			  "human_discrimination_upper_95": null,
			  "limitations": ["not a substitute for independent sensory validation"],
			  "simulation_status": "completed",
			  "simulation_confidence": 0.58,
			  "simulation_p05": 41.2,
			  "simulation_p95": 79.8,
			  "simulation_draws": 500,
			  "physsim_status": "diagnostic_only",
			  "physsim_similarity_score": 0.42,
			  "physsim_learned_r2_status": "weight_zero_gate_failed",
			  "physsim_learned_r2_member_disagreement_percent": 12.5
			}""";

	private CandidateVersionRawView view(String raw) {
		return new CandidateVersionRawView(500L, 900L, raw);
	}

	@Test
	void maps_known_scalars_and_collects_physsim_diagnostics() {
		PredictionResponse response = mapper.toResponse(view(RAW_RESPONSE));

		assertThat(response.similarityScore()).isEqualTo(87.42);
		assertThat(response.similarityKind()).isEqualTo("semantic_profile_proxy");
		assertThat(response.modelApplicabilityPercent()).isEqualTo(64.0);
		assertThat(response.scientificModelDomainPassed()).isTrue();

		assertThat(response.humanValidation().similarity90ClaimAuthorized()).isFalse();
		assertThat(response.humanValidation().actualOlfactorySimilarityScore()).isNull();

		assertThat(response.simulation().status()).isEqualTo("completed");
		assertThat(response.simulation().draws()).isEqualTo(500);

		assertThat(response.limitations().size()).isEqualTo(1);

		assertThat(response.diagnostics()).isNotNull();
		assertThat(response.diagnostics().has("physsim_status")).isTrue();
		assertThat(response.diagnostics().has("physsim_learned_r2_member_disagreement_percent")).isTrue();
		assertThat(response.diagnostics().has("status")).isFalse(); // physsim_ 접두 아닌 필드는 제외
	}

	@Test
	void uncertainty_view_carries_only_diagnostic_subset() {
		PredictionUncertaintyResponse uncertainty = mapper.toUncertaintyResponse(view(RAW_RESPONSE));

		assertThat(uncertainty.modelApplicabilityPercent()).isEqualTo(64.0);
		assertThat(uncertainty.scientificUncertaintyKind()).isEqualTo("monte_carlo_quantile");
		assertThat(uncertainty.simulation().p05()).isEqualTo(41.2);
		assertThat(uncertainty.diagnostics().has("physsim_similarity_score")).isTrue();
	}

	@Test
	void unparsable_raw_response_yields_null_scalars_without_failing() {
		PredictionResponse response = mapper.toResponse(view("not json"));

		assertThat(response.candidateId()).isEqualTo(500L);
		assertThat(response.similarityScore()).isNull();
		assertThat(response.humanValidation()).isNotNull();
		assertThat(response.humanValidation().similarity90ClaimAuthorized()).isNull();
		assertThat(response.diagnostics()).isNull();
	}
}
