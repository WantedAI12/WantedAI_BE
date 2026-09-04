package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.entity.CandidateVersion;
import com.perfumeryaicore.domain.formula.entity.CandidateVersionIngredient;
import com.perfumeryaicore.domain.formula.service.CandidateVersionMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Modal 라이브 검증(2026-09-02)에서 확인한 실제 응답 구조를 기준으로 시간 변화 필드 재파싱을 검증한다.
 */
class CandidateVersionMapperTest {

	private final CandidateVersionMapper mapper = new CandidateVersionMapper();

	private static final String RAW_RESPONSE = """
			{
			  "status": "prototype_ready",
			  "message": "안전 조건 충족",
			  "temporal_timepoints_minutes": [0, 15, 60, 240, 480],
			  "temporal_profile": [
			    {"minutes": 0, "phase": "opening", "relative_to_opening_intensity_percent": 100.0},
			    {"minutes": 480, "phase": "drydown", "relative_to_opening_intensity_percent": 4.2}
			  ],
			  "ingredient_temporal_profile": [
			    {"ingredient_id": "dihydromyrcenol", "name": "Dihydromyrcenol",
			     "points": [{"minutes": 0, "estimated_remaining_concentrate_percent": 23.4984}]}
			  ],
			  "temporal_concentration_basis": "first-order surface evaporation proxy",
			  "temporal_model_claim_boundary": "Estimated post-application surface residue; not a sealed-bottle assay.",
			  "deployment": {"provider": "modal", "gpu_required": false}
			}""";

	private CandidateVersion versionWithRaw(String raw) {
		CandidateVersion version = CandidateVersion.builder()
				.candidateId(500L)
				.cost(42.0)
				.aiProvider("modal")
				.rawResponse(raw)
				.createdBy(1L)
				.build();
		ReflectionTestUtils.setField(version, "id", 900L);
		return version;
	}

	@Test
	void temporal_fields_are_reparsed_from_stored_raw_response() {
		CandidateVersion version = versionWithRaw(RAW_RESPONSE);

		CandidateVersionResponse response = mapper.toResponse(version, List.of());

		assertThat(response.versionId()).isEqualTo(900L);
		assertThat(response.temporal()).isNotNull();
		assertThat(response.temporal().timepointsMinutes()).containsExactly(0, 15, 60, 240, 480);
		assertThat(response.temporal().profile()).hasSize(2);
		assertThat(response.temporal().ingredientProfile()).hasSize(1);
		assertThat(response.temporal().claimBoundary()).contains("not a sealed-bottle assay");
	}

	@Test
	void ingredients_are_mapped_from_entities_not_raw_json() {
		CandidateVersionIngredient line = CandidateVersionIngredient.builder()
				.candidateVersionId(900L)
				.ingredientExternalId("dihydromyrcenol")
				.ingredientName("Dihydromyrcenol")
				.pyramid("top")
				.concentratePercent(23.4984)
				.build();

		CandidateVersionResponse response = mapper.toResponse(versionWithRaw(RAW_RESPONSE), List.of(line));

		assertThat(response.ingredients()).hasSize(1);
		assertThat(response.ingredients().get(0).ingredientId()).isEqualTo("dihydromyrcenol");
		assertThat(response.ingredients().get(0).concentratePercent()).isEqualTo(23.4984);
	}

	@Test
	void missing_or_unparsable_raw_response_yields_null_temporal_without_failing() {
		CandidateVersionResponse withNull = mapper.toResponse(versionWithRaw(null), List.of());
		assertThat(withNull.temporal()).isNull();

		CandidateVersionResponse withGarbage = mapper.toResponse(versionWithRaw("not json"), List.of());
		assertThat(withGarbage.temporal()).isNull();
	}
}
