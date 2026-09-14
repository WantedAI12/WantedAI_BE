package com.perfumeryaicore.domain.lotion;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.formula.service.CandidateVersionRawView;
import com.perfumeryaicore.domain.lotion.dto.response.LotionDetailResponse;
import com.perfumeryaicore.domain.lotion.service.LotionDetailMapper;
import org.junit.jupiter.api.Test;

/** 실제 배포 서버 라이브 응답(2026-09-14 확인)의 최상위 필드 구조를 기준으로 검증한다. */
class LotionDetailMapperTest {

	private final LotionDetailMapper mapper = new LotionDetailMapper();

	private static final String RAW_RESPONSE = """
			{
			  "score_kind": "exposure_integrated_observed_reference_agreement_not_user_similarity",
			  "human_similarity_percent": null,
			  "manufacturing_approved": false,
			  "all_user_requirements_verified": false,
			  "status": "insufficient_observed_target_coverage",
			  "score": null,
			  "profile_target_met": false,
			  "recipe": [],
			  "closest_candidate": [],
			  "search_incomplete": true,
			  "candidate_use": "unvalidated_research_formula_not_manufacturing_instruction",
			  "product_model": {
			    "version": "lotion-observed-reference/v1",
			    "cross_product_scores_comparable": false
			  },
			  "preparation": {
			    "intent": {
			      "original_text": "은은한 시트러스 향의 산뜻한 바디로션을 원합니다."
			    }
			  },
			  "perception_model": {
			    "limitations": [
			      "Atlas applicability and descriptor-use measurements are distinct, separately normalized reference shapes, not absolute intensities."
			    ],
			    "evaluated_timepoint_count": 0
			  }
			}""";

	private CandidateVersionRawView view(String raw) {
		return new CandidateVersionRawView(500L, 900L, raw);
	}

	@Test
	void maps_top_level_scalars_and_passes_through_nested_structures_raw() {
		LotionDetailResponse response = mapper.toResponse(view(RAW_RESPONSE));

		assertThat(response.status()).isEqualTo("insufficient_observed_target_coverage");
		assertThat(response.profileTargetMet()).isFalse();
		assertThat(response.searchIncomplete()).isTrue();
		assertThat(response.candidateUse()).isEqualTo("unvalidated_research_formula_not_manufacturing_instruction");
		assertThat(response.scoreKind()).isEqualTo("exposure_integrated_observed_reference_agreement_not_user_similarity");
		assertThat(response.manufacturingApproved()).isFalse();
		assertThat(response.allUserRequirementsVerified()).isFalse();
		assertThat(response.humanSimilarityPercent()).isNull();

		assertThat(response.productModel()).isNotNull();
		assertThat(response.productModel().path("version").asString()).isEqualTo("lotion-observed-reference/v1");
		assertThat(response.preparation().path("intent").path("original_text").asString())
				.contains("바디로션");
		assertThat(response.perceptionModel().path("limitations").get(0).asString())
				.contains("Atlas applicability");
		assertThat(response.perceptionModel().path("evaluated_timepoint_count").asInt()).isZero();
	}

	@Test
	void unparsable_raw_response_yields_an_empty_response_without_failing() {
		LotionDetailResponse response = mapper.toResponse(view("not json"));

		assertThat(response.candidateId()).isEqualTo(500L);
		assertThat(response.versionId()).isEqualTo(900L);
		assertThat(response.status()).isNull();
		assertThat(response.productModel()).isNull();
	}

	@Test
	void a_string_score_value_is_preserved_without_coercion_attempts() {
		String raw = RAW_RESPONSE.replace("\"score\": null,", "\"score\": \"heuristic_only\",");

		LotionDetailResponse response = mapper.toResponse(view(raw));

		assertThat(response.score().asString()).isEqualTo("heuristic_only");
	}
}
