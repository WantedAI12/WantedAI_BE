package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.IngredientLine;
import com.perfumeryaicore.domain.formula.service.CandidateDiagnosticReviseService;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.request.dto.request.BriefReviewRequest;
import com.perfumeryaicore.domain.request.dto.request.ReassessDiagnosticRequest;
import com.perfumeryaicore.domain.request.dto.request.ReviseCandidateApiRequest;
import com.perfumeryaicore.domain.request.service.BriefReviewService;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.DiagnosticCandidate;
import com.perfumeryaicore.global.client.dto.EvaluationResponse;
import com.perfumeryaicore.global.client.dto.PrepareBriefResponse;
import com.perfumeryaicore.global.client.dto.ReviseCandidateResponse;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

/** BE-102: 저장된 후보의 현재 배합을 prepare→reassess(진단)→revise 3단계로 이어 자연어 수정 검토한다. */
class CandidateDiagnosticReviseServiceTest {

	private static final long CANDIDATE_ID = 900L;
	private static final long REQUEST_ID = 5L;
	private static final long MEMBER_ID = 1L;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final CandidateService candidateService = mock(CandidateService.class);
	private final BriefReviewService briefReviewService = mock(BriefReviewService.class);
	private final CandidateDiagnosticReviseService service =
			new CandidateDiagnosticReviseService(candidateService, briefReviewService);

	private CandidateResponse candidateWithIngredients() {
		CandidateVersionResponse version = new CandidateVersionResponse(
				1200L, CANDIDATE_ID, null,
				List.of(new IngredientLine("linalyl_acetate", "Linalyl Acetate", "top", 100.0, null, null, null)),
				42.0, null, null, null, MEMBER_ID, LocalDateTime.now(), null);
		return new CandidateResponse(CANDIDATE_ID, REQUEST_ID, CandidateStatus.UNDER_REVIEW, version, null, null, null);
	}

	private PrepareBriefResponse readyPrepare(String reviewId) {
		return new PrepareBriefResponse("rd-brief-2", "ready", List.of(), List.of(), List.of(),
				JSON.createObjectNode(), JSON.createObjectNode(), null, JSON.createObjectNode(),
				JSON.createObjectNode(), reviewId, true, false, "result-1");
	}

	private PrepareBriefResponse needsInputPrepare() {
		return new PrepareBriefResponse("rd-brief-2", "needs_input", List.of("request.formula.max_formula_cost_per_kg"),
				List.of(), List.of(), JSON.createObjectNode(), JSON.createObjectNode(), null,
				JSON.createObjectNode(), JSON.createObjectNode(), null, true, false, "result-2");
	}

	private EvaluationResponse diagnosticEvaluation(String snapshotCandidateId) {
		DiagnosticCandidate candidate = new DiagnosticCandidate(
				snapshotCandidateId, "formula-1", "abstained", "fixed_formula", 91.1, "model_points_0_100",
				15.0, 1, false, JSON.createObjectNode(), JSON.createObjectNode(), JSON.createObjectNode(),
				JSON.createObjectNode(), JSON.createObjectNode(), JSON.createObjectNode());
		return new EvaluationResponse("rd-candidates-2", "review-1", JSON.createObjectNode(), "abstained",
				List.of(), List.of(candidate), false, false, true,
				"explicit_public_source_diagnostic_not_operational_recommendation",
				JSON.createObjectNode(), "result-3");
	}

	@Test
	void revise_chains_prepare_reassess_and_revise_with_the_current_recipe() {
		when(candidateService.get(CANDIDATE_ID, MEMBER_ID)).thenReturn(candidateWithIngredients());
		ArgumentCaptor<BriefReviewRequest> prepareCaptor = ArgumentCaptor.forClass(BriefReviewRequest.class);
		when(briefReviewService.prepare(eq(REQUEST_ID), eq(MEMBER_ID), prepareCaptor.capture()))
				.thenReturn(readyPrepare("review-9"));

		ArgumentCaptor<ReassessDiagnosticRequest> reassessCaptor =
				ArgumentCaptor.forClass(ReassessDiagnosticRequest.class);
		when(briefReviewService.reassessDiagnostic(eq(REQUEST_ID), eq(MEMBER_ID), reassessCaptor.capture()))
				.thenReturn(new PerfumeryAiResult<>(
						"{\"raw\":true}", diagnosticEvaluation("snapshot-1"), 10L));

		ArgumentCaptor<ReviseCandidateApiRequest> reviseCaptor =
				ArgumentCaptor.forClass(ReviseCandidateApiRequest.class);
		ReviseCandidateResponse reviseResponse = new ReviseCandidateResponse(
				"rd-revision-2", JSON.createObjectNode(), JSON.createObjectNode(), JSON.createObjectNode(),
				1, true, "evaluate", "scope");
		when(briefReviewService.reviseCandidate(eq(REQUEST_ID), eq(MEMBER_ID), reviseCaptor.capture()))
				.thenReturn(reviseResponse);

		ReviseCandidateResponse response = service.revise(CANDIDATE_ID, MEMBER_ID, "우디 느낌을 더 강하게");

		assertThat(response.nextOperation()).isEqualTo("evaluate");
		assertThat(prepareCaptor.getValue().lines()).hasSize(1);
		assertThat(prepareCaptor.getValue().lines().get(0).ingredientId()).isEqualTo("linalyl_acetate");
		assertThat(prepareCaptor.getValue().isDiagnosticOnly()).isTrue();
		assertThat(reassessCaptor.getValue().confirmedReviewId()).isEqualTo("review-9");
		assertThat(reassessCaptor.getValue().lines()).hasSize(1);
		assertThat(reviseCaptor.getValue().source().candidateId()).isEqualTo("snapshot-1");
		assertThat(reviseCaptor.getValue().source().backendVersionId()).isEqualTo("candidate-900-v1200");
		assertThat(reviseCaptor.getValue().instruction()).isEqualTo("우디 느낌을 더 강하게");
	}

	@Test
	void revise_fails_when_the_candidate_has_no_current_version() {
		CandidateResponse candidate = new CandidateResponse(
				CANDIDATE_ID, REQUEST_ID, CandidateStatus.UNDER_REVIEW, null, null, null, null);
		when(candidateService.get(CANDIDATE_ID, MEMBER_ID)).thenReturn(candidate);

		assertThatThrownBy(() -> service.revise(CANDIDATE_ID, MEMBER_ID, "지시"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_VERSION_NOT_FOUND);
	}

	@Test
	void revise_fails_when_prepare_is_not_ready() {
		when(candidateService.get(CANDIDATE_ID, MEMBER_ID)).thenReturn(candidateWithIngredients());
		when(briefReviewService.prepare(eq(REQUEST_ID), eq(MEMBER_ID), any())).thenReturn(needsInputPrepare());

		assertThatThrownBy(() -> service.revise(CANDIDATE_ID, MEMBER_ID, "지시"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_REVISE_PREPARE_NOT_READY);
	}

	@Test
	void revise_fails_when_reassess_returns_no_diagnostic_candidates() {
		when(candidateService.get(CANDIDATE_ID, MEMBER_ID)).thenReturn(candidateWithIngredients());
		when(briefReviewService.prepare(eq(REQUEST_ID), eq(MEMBER_ID), any())).thenReturn(readyPrepare("review-9"));
		EvaluationResponse emptyEvaluation = new EvaluationResponse("rd-candidates-2", "review-1",
				JSON.createObjectNode(), "abstained", List.of(), List.of(), false, false, true,
				"scope", JSON.createObjectNode(), "result-3");
		when(briefReviewService.reassessDiagnostic(eq(REQUEST_ID), eq(MEMBER_ID), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", emptyEvaluation, 10L));

		assertThatThrownBy(() -> service.revise(CANDIDATE_ID, MEMBER_ID, "지시"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CANDIDATE_REVISE_NO_DIAGNOSTIC_RESULT);
	}
}
