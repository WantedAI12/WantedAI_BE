package com.perfumeryaicore.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.IngredientLine;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.safety.dto.request.AssessEvidenceApiRequest;
import com.perfumeryaicore.domain.safety.dto.request.ChangeImpactApiRequest;
import com.perfumeryaicore.domain.safety.service.RegulatoryEvidenceService;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.AiCapabilitiesResponse;
import com.perfumeryaicore.global.client.dto.AssessEvidenceRequest;
import com.perfumeryaicore.global.client.dto.AssessEvidenceResponse;
import com.perfumeryaicore.global.client.dto.ChangeImpactRequest;
import com.perfumeryaicore.global.client.dto.ChangeImpactResponse;
import com.perfumeryaicore.global.client.dto.EvidenceCoverageResponse;
import com.perfumeryaicore.global.client.dto.EvidenceStatusResponse;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** v2 연동 답변(2026-09-15) 2단계: 규제 근거 조회 3종의 위임을 검증한다. */
class RegulatoryEvidenceServiceTest {

	private static final long CANDIDATE_ID = 900L;
	private static final long MEMBER_ID = 1L;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final CandidateService candidateService = mock(CandidateService.class);
	private final PerfumeryAiClient perfumeryAiClient = mock(PerfumeryAiClient.class);
	private final RegulatoryEvidenceService service =
			new RegulatoryEvidenceService(candidateService, perfumeryAiClient);

	private CandidateResponse candidateWithIngredients() {
		CandidateVersionResponse version = new CandidateVersionResponse(
				1200L, CANDIDATE_ID, null,
				List.of(new IngredientLine("linalyl_acetate", "Linalyl Acetate", "top", 100.0, null, null, null)),
				42.0, null, null, null, null, null, null, MEMBER_ID, LocalDateTime.now(), null);
		return new CandidateResponse(CANDIDATE_ID, 5L, CandidateStatus.UNDER_REVIEW, version, null, null, null);
	}

	@Test
	void status_delegates_to_the_ai_client() {
		EvidenceStatusResponse response = new EvidenceStatusResponse(
				"rd-evidence-status-1", JSON.createObjectNode(), false, true, true, false, JSON.createObjectNode());
		when(perfumeryAiClient.evidenceStatus(any())).thenReturn(new PerfumeryAiResult<>("{}", response, 0L));

		EvidenceStatusResponse result = service.status(MEMBER_ID);

		assertThat(result.publicSourcesRegistered()).isTrue();
	}

	@Test
	void coverage_clamps_limit_to_the_modal_maximum_of_500() {
		EvidenceCoverageResponse response = new EvidenceCoverageResponse(
				3830, 243, 3587, 0, 1212, "scope", 0, 500, List.of(), true);
		ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
		when(perfumeryAiClient.evidenceCoverage(org.mockito.ArgumentMatchers.eq(0), limitCaptor.capture(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", response, 0L));

		service.coverage(MEMBER_ID, 0, 5000);

		assertThat(limitCaptor.getValue()).isEqualTo(500);
	}

	@Test
	void coverage_defaults_offset_and_limit_when_not_given() {
		EvidenceCoverageResponse response = new EvidenceCoverageResponse(
				3830, 243, 3587, 0, 1212, "scope", 0, 100, List.of(), false);
		when(perfumeryAiClient.evidenceCoverage(0, 100, "evidence-coverage-" + MEMBER_ID))
				.thenReturn(new PerfumeryAiResult<>("{}", response, 0L));

		service.coverage(MEMBER_ID, null, null);

		verify(perfumeryAiClient).evidenceCoverage(0, 100, "evidence-coverage-" + MEMBER_ID);
	}

	@Test
	void assess_builds_lines_from_the_candidate_current_version() {
		when(candidateService.get(CANDIDATE_ID, MEMBER_ID)).thenReturn(candidateWithIngredients());
		ArgumentCaptor<AssessEvidenceRequest> captor = ArgumentCaptor.forClass(AssessEvidenceRequest.class);
		AssessEvidenceResponse response = new AssessEvidenceResponse(
				"rd-evidence-assessment-1", null, "2026-09-15", "input-1", JSON.createObjectNode(),
				JSON.createObjectNode(), "blocked", false,
				List.of(), JSON.createObjectNode(), null, null, false, JSON.createObjectNode(), "scope",
				JSON.createObjectNode(), "result-1");
		when(perfumeryAiClient.assessEvidence(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", response, 0L));

		AssessEvidenceApiRequest dto = new AssessEvidenceApiRequest(
				TargetRegion.EU, ProductCategory.EAU_DE_PARFUM, 15.0, 180.0, null, 1000.0, 10, 100.0);
		AssessEvidenceResponse result = service.assess(CANDIDATE_ID, MEMBER_ID, dto);

		assertThat(result.status()).isEqualTo("blocked");
		assertThat(captor.getValue().lines()).hasSize(1);
		assertThat(captor.getValue().lines().get(0).ingredientId()).isEqualTo("linalyl_acetate");
		assertThat(captor.getValue().lines().get(0).concentratePercent()).isEqualTo(100.0);
		assertThat(captor.getValue().targetRegion()).isEqualTo("EU");
		assertThat(captor.getValue().productCategory()).isEqualTo("eau_de_parfum");
		assertThat(captor.getValue().policy().get("finished_batch_mass_g").asDouble()).isEqualTo(1000.0);
	}

	@Test
	void capabilities_delegates_to_the_ai_client() {
		AiCapabilitiesResponse response = new AiCapabilitiesResponse(
				"ai-capabilities-1", List.of("eau_de_parfum"), JSON.createObjectNode(), JSON.createObjectNode(),
				JSON.createObjectNode(), JSON.createObjectNode(), JSON.createObjectNode(), JSON.createObjectNode(),
				JSON.createObjectNode(), JSON.createObjectNode(), JSON.createObjectNode(), JSON.createObjectNode(),
				JSON.createObjectNode(), JSON.createObjectNode(), "scope");
		when(perfumeryAiClient.capabilities()).thenReturn(response);

		AiCapabilitiesResponse result = service.capabilities();

		assertThat(result.supportedProductCodes()).containsExactly("eau_de_parfum");
	}

	@Test
	void assess_rejects_a_candidate_without_a_current_version() {
		CandidateResponse candidate = new CandidateResponse(
				CANDIDATE_ID, 5L, CandidateStatus.UNDER_REVIEW, null, null, null, null);
		when(candidateService.get(CANDIDATE_ID, MEMBER_ID)).thenReturn(candidate);
		AssessEvidenceApiRequest dto = new AssessEvidenceApiRequest(
				TargetRegion.EU, ProductCategory.EAU_DE_PARFUM, 15.0, 180.0, null, null, null, null);

		assertThatThrownBy(() -> service.assess(CANDIDATE_ID, MEMBER_ID, dto))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
	}

	@Test
	void changeImpact_builds_lines_from_the_candidate_current_version_and_forwards_the_previous_version() {
		when(candidateService.get(CANDIDATE_ID, MEMBER_ID)).thenReturn(candidateWithIngredients());
		ArgumentCaptor<ChangeImpactRequest> captor = ArgumentCaptor.forClass(ChangeImpactRequest.class);
		ChangeImpactResponse response = new ChangeImpactResponse("rd-change-impact-1",
				JSON.createObjectNode(), JSON.createObjectNode(), JSON.createObjectNode(), 1, true, false, false,
				"public_observation_comparison_not_operator_approval_or_inventory_reservation", "result-1",
				JSON.createObjectNode());
		when(perfumeryAiClient.changeImpact(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", response, 0L));

		ChangeImpactApiRequest dto = new ChangeImpactApiRequest(
				"evidence-v3", TargetRegion.EU, ProductCategory.EAU_DE_PARFUM, 15.0, 180.0, null, 1000.0, 10, 100.0);
		ChangeImpactResponse result = service.changeImpact(CANDIDATE_ID, MEMBER_ID, dto);

		assertThat(result.affectedMaterialCount()).isEqualTo(1);
		assertThat(result.reviewRequired()).isTrue();
		assertThat(captor.getValue().previousEvidenceVersion()).isEqualTo("evidence-v3");
		assertThat(captor.getValue().lines()).hasSize(1);
		assertThat(captor.getValue().lines().get(0).ingredientId()).isEqualTo("linalyl_acetate");
		assertThat(captor.getValue().targetRegion()).isEqualTo("EU");
		assertThat(captor.getValue().policy().get("finished_batch_mass_g").asDouble()).isEqualTo(1000.0);
	}

	@Test
	void changeImpact_rejects_a_candidate_without_a_current_version() {
		CandidateResponse candidate = new CandidateResponse(
				CANDIDATE_ID, 5L, CandidateStatus.UNDER_REVIEW, null, null, null, null);
		when(candidateService.get(CANDIDATE_ID, MEMBER_ID)).thenReturn(candidate);
		ChangeImpactApiRequest dto = new ChangeImpactApiRequest(
				"evidence-v3", TargetRegion.EU, ProductCategory.EAU_DE_PARFUM, 15.0, 180.0, null, null, null, null);

		assertThatThrownBy(() -> service.changeImpact(CANDIDATE_ID, MEMBER_ID, dto))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
	}
}
