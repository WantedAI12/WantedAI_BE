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

	/**
	 * AI팀 확인(2026-09-18) - 실제 Modal 라이브 응답으로 확인한 구조. 원료 배합비 목록만 보여주던
	 * 화면 대신 이 서술형 설명을 쓸 수 있다.
	 */
	@Test
	void perfumer_notes_text_is_reparsed_from_stored_raw_response() {
		String rawWithNotes = """
				{
				  "status": "prototype_ready",
				  "message": "안전 조건 충족",
				  "perfumer_notes": {
				    "schema_version": "perfumer-notes/v1",
				    "text": "향의 콘셉트\\n요청하신 것을 출발점으로 구성한 배합입니다."
				  }
				}""";

		CandidateVersionResponse response = mapper.toResponse(versionWithRaw(rawWithNotes), List.of());

		assertThat(response.perfumerNotes()).contains("향의 콘셉트");
	}

	@Test
	void missing_perfumer_notes_yields_null_without_failing() {
		CandidateVersionResponse response = mapper.toResponse(versionWithRaw(RAW_RESPONSE), List.of());

		assertThat(response.perfumerNotes()).isNull();
	}

	/**
	 * AI팀 확인(2026-09-18) - 후보 설명 응답 확장. 값을 재해석하지 않고 원문 그대로 노출해야
	 * null/unknown 구분, 통화 미표기 등 세부 값이 그대로 보존된다.
	 */
	@Test
	void candidate_and_safety_explanation_are_reparsed_from_stored_raw_response() {
		String rawWithExplanations = """
				{
				  "status": "prototype_ready",
				  "message": "안전 조건 충족",
				  "candidate_explanation": {"ingredients": [{"price_currency": null}]},
				  "safety_explanation": {"internal_review_passed": true, "regulatory_status": "unknown"}
				}""";

		CandidateVersionResponse response = mapper.toResponse(versionWithRaw(rawWithExplanations), List.of());

		assertThat(response.candidateExplanation().path("ingredients").get(0).path("price_currency").isNull())
				.isTrue();
		assertThat(response.safetyExplanation().path("regulatory_status").asString()).isEqualTo("unknown");
	}

	@Test
	void missing_candidate_and_safety_explanation_yield_null_without_failing() {
		CandidateVersionResponse response = mapper.toResponse(versionWithRaw(RAW_RESPONSE), List.of());

		assertThat(response.candidateExplanation()).isNull();
		assertThat(response.safetyExplanation()).isNull();
	}
}
