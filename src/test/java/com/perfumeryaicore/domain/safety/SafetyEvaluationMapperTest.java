package com.perfumeryaicore.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.formula.service.CandidateVersionRawView;
import com.perfumeryaicore.domain.safety.dto.response.SafetyEvaluationResponse;
import com.perfumeryaicore.domain.safety.service.SafetyEvaluationMapper;
import org.junit.jupiter.api.Test;

/** 2026-09-02 Modal 라이브 검증에서 확인한 safety 구간 실제 키 구성을 기준으로 검증한다. */
class SafetyEvaluationMapperTest {

	private final SafetyEvaluationMapper mapper = new SafetyEvaluationMapper();

	private static final String RAW_RESPONSE = """
			{
			  "status": "prototype_ready",
			  "safety": {
			    "status": "PASSED",
			    "internal_gate_passed": true,
			    "manufacturing_ready": false,
			    "validation_level": "internal",
			    "evidence_coverage_percent": 62.5,
			    "regulatory_data_complete": false,
			    "internal_evidence_complete": true,
			    "allergen_quantification_complete": false,
			    "target_region": "EU",
			    "product_category": "eau_de_parfum",
			    "audit_id": "audit-9001",
			    "standards_checked_on": "2026-09-01",
			    "standards_review_due": "2027-03-01",
			    "violations": [],
			    "warnings": ["allergen quantification pending"],
			    "missing_documents": ["ifra_certificate"],
			    "potential_eu_allergens": ["limonene"]
			  }
			}""";

	private CandidateVersionRawView view(String raw) {
		return new CandidateVersionRawView(500L, 900L, raw);
	}

	@Test
	void maps_known_safety_fields_from_raw_response() {
		SafetyEvaluationResponse response = mapper.toResponse(view(RAW_RESPONSE));

		assertThat(response.candidateId()).isEqualTo(500L);
		assertThat(response.versionId()).isEqualTo(900L);
		assertThat(response.status()).isEqualTo("PASSED");
		assertThat(response.internalGatePassed()).isTrue();
		assertThat(response.manufacturingReady()).isFalse();
		assertThat(response.evidenceCoveragePercent()).isEqualTo(62.5);
		assertThat(response.targetRegion()).isEqualTo("EU");
		assertThat(response.auditId()).isEqualTo("audit-9001");
		assertThat(response.warnings().size()).isEqualTo(1);
		assertThat(response.missingDocuments().get(0).asString()).isEqualTo("ifra_certificate");
		assertThat(response.potentialEuAllergens().get(0).asString()).isEqualTo("limonene");
	}

	@Test
	void missing_safety_section_yields_all_null_fields_without_failing() {
		SafetyEvaluationResponse response = mapper.toResponse(view("{\"status\":\"prototype_ready\"}"));

		assertThat(response.candidateId()).isEqualTo(500L);
		assertThat(response.status()).isNull();
		assertThat(response.internalGatePassed()).isNull();
		assertThat(response.violations()).isNull();
	}

	@Test
	void null_or_unparsable_raw_response_does_not_throw() {
		assertThat(mapper.toResponse(view(null)).status()).isNull();
		assertThat(mapper.toResponse(view("not json")).status()).isNull();
	}
}
