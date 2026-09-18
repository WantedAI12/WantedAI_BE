package com.perfumeryaicore.domain.request.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.request.dto.request.BriefClarifyRequest;
import com.perfumeryaicore.domain.request.dto.request.BriefReviewRequest;
import com.perfumeryaicore.domain.request.dto.request.CompareCandidatesApiRequest;
import com.perfumeryaicore.domain.request.dto.request.EvaluateDiagnosticRequest;
import com.perfumeryaicore.domain.request.dto.request.ReassessDiagnosticRequest;
import com.perfumeryaicore.domain.request.dto.request.ReviseCandidateApiRequest;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.entity.Intensity;
import com.perfumeryaicore.domain.request.entity.Longevity;
import com.perfumeryaicore.global.client.ModalAiProperties;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.ClarifyBriefRequest;
import com.perfumeryaicore.global.client.dto.ClarifyBriefResponse;
import com.perfumeryaicore.global.client.dto.CompareCandidatesRequest;
import com.perfumeryaicore.global.client.dto.CompareCandidatesResponse;
import com.perfumeryaicore.global.client.dto.EvaluateFormulaRequest;
import com.perfumeryaicore.global.client.dto.EvaluationResponse;
import com.perfumeryaicore.global.client.dto.EvidenceLine;
import com.perfumeryaicore.global.client.dto.PrepareBriefRequest;
import com.perfumeryaicore.global.client.dto.PrepareBriefResponse;
import com.perfumeryaicore.global.client.dto.ReassessFormulaRequest;
import com.perfumeryaicore.global.client.dto.ReviseCandidateRequest;
import com.perfumeryaicore.global.client.dto.ReviseCandidateResponse;
import com.perfumeryaicore.global.client.dto.StoredCandidate;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.TargetRegion;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** v2 연동 답변(2026-09-15) 1단계: /v2/briefs/prepare, /v2/briefs/clarify 위임을 검증한다. */
class BriefReviewServiceTest {

	private static final long REQUEST_ID = 500L;
	private static final long MEMBER_ID = 1L;
	private static final double KRW_PER_USD = 1350.0;
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final FragranceRequestService requestService = mock(FragranceRequestService.class);
	private final PerfumeryAiClient perfumeryAiClient = mock(PerfumeryAiClient.class);
	private final ModalAiProperties modalAiProperties = new ModalAiProperties("http://ai.local", "token",
			java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2), 30, 1, KRW_PER_USD);
	private final BriefReviewService service =
			new BriefReviewService(requestService, perfumeryAiClient, modalAiProperties);

	private FragranceRequest fullyStructuredRequest() {
		FragranceRequest request = FragranceRequest.create(10L, MEMBER_ID, "피오니와 청사과 향");
		request.applyUpdate(null, ProductCategory.EAU_DE_PARFUM, TargetRegion.EU, 2,
				Intensity.MODERATE, Longevity.HIGH, 15.0, 30, 180.0, List.of());
		return request;
	}

	private PrepareBriefResponse readyResponse(String reviewId) {
		return new PrepareBriefResponse("rd-brief-2", "ready", List.of(), List.of(), List.of(),
				JSON.createObjectNode(), JSON.createObjectNode(), null, JSON.createObjectNode(),
				JSON.createObjectNode(), reviewId, true, false, "result-1");
	}

	@Test
	void prepare_maps_the_stored_request_into_the_v2_formula_schema() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<PrepareBriefRequest> captor = ArgumentCaptor.forClass(PrepareBriefRequest.class);
		when(perfumeryAiClient.prepareBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		PrepareBriefResponse response = service.prepare(REQUEST_ID, MEMBER_ID, BriefReviewRequest.empty());

		assertThat(response.isReady()).isTrue();
		assertThat(response.reviewId()).isEqualTo("review-1");
		JsonNode formula = captor.getValue().request().get("formula");
		assertThat(formula.get("brief").asString()).isEqualTo("피오니와 청사과 향");
		assertThat(formula.get("max_risk_tier").asInt()).isEqualTo(2);
		assertThat(formula.get("product_concentration_percent").asDouble()).isEqualTo(15.0);
		// 저장값은 원화(KRW/kg)지만 Modal에는 USD/kg로 환산해 보낸다.
		assertThat(formula.get("max_ingredient_price_per_kg").asDouble()).isEqualTo(180.0 / KRW_PER_USD);
		assertThat(formula.get("target_region").asString()).isEqualTo("EU");
		assertThat(formula.get("product_category").asString()).isEqualTo("eau_de_parfum");
		assertThat(formula.get("accords").isArray()).isTrue();
		assertThat(formula.get("accords")).isEmpty();
	}

	/**
	 * AI팀 확인(2026-09-19): v1 FormulaRequestMapper와 같은 필드 매핑을 유지한다 - 실제 생성과
	 * 이 리뷰 경로가 같은 향 계열 정보를 봐야 한다.
	 */
	@Test
	void prepare_forwards_the_stored_accords_into_the_formula_schema() {
		FragranceRequest request = FragranceRequest.create(10L, MEMBER_ID, "피오니와 청사과 향");
		request.applyUpdate(null, ProductCategory.EAU_DE_PARFUM, TargetRegion.EU, 2,
				Intensity.MODERATE, Longevity.HIGH, 15.0, 30, 180.0, List.of("시트러스", "우디"));
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(request);
		ArgumentCaptor<PrepareBriefRequest> captor = ArgumentCaptor.forClass(PrepareBriefRequest.class);
		when(perfumeryAiClient.prepareBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		service.prepare(REQUEST_ID, MEMBER_ID, BriefReviewRequest.empty());

		JsonNode accords = captor.getValue().request().get("formula").get("accords");
		assertThat(accords.get(0).asString()).isEqualTo("시트러스");
		assertThat(accords.get(1).asString()).isEqualTo("우디");
	}

	/**
	 * FragranceRequest에 max_formula_cost_per_kg를 담을 필드가 아직 없어, 비워서 보내면 v2
	 * prepare가 매번 needs_input으로 이 필드를 되물어 진행이 막힌다(AI 확인) - Modal 스키마
	 * 자체 기본값(180.0)을 항상 채워 보낸다.
	 */
	@Test
	void prepare_always_fills_max_formula_cost_per_kg_with_the_modal_default() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<PrepareBriefRequest> captor = ArgumentCaptor.forClass(PrepareBriefRequest.class);
		when(perfumeryAiClient.prepareBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		service.prepare(REQUEST_ID, MEMBER_ID, BriefReviewRequest.empty());

		assertThat(captor.getValue().request().get("formula").get("max_formula_cost_per_kg").asDouble())
				.isEqualTo(180.0);
	}

	/** reassess 진행 순서 1단계(AI 확인): 고정 배합을 리뷰할 때는 prepare에도 같은 lines를 넣는다. */
	@Test
	void prepare_forwards_lines_when_reviewing_a_fixed_formula() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<PrepareBriefRequest> captor = ArgumentCaptor.forClass(PrepareBriefRequest.class);
		when(perfumeryAiClient.prepareBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		service.prepare(REQUEST_ID, MEMBER_ID,
				new BriefReviewRequest(null, null, null, List.of(new EvidenceLine("linalyl_acetate", 100.0)), true));

		JsonNode lines = captor.getValue().lines();
		assertThat(lines.get(0).get("ingredient_id").asString()).isEqualTo("linalyl_acetate");
		assertThat(lines.get(0).get("concentrate_percent").asDouble()).isEqualTo(100.0);
	}

	@Test
	void prepare_includes_only_the_evidence_policy_fields_that_were_provided() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<PrepareBriefRequest> captor = ArgumentCaptor.forClass(PrepareBriefRequest.class);
		when(perfumeryAiClient.prepareBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		service.prepare(REQUEST_ID, MEMBER_ID, new BriefReviewRequest(1000.0, null, null, null, null));

		JsonNode policy = captor.getValue().evidencePolicy();
		assertThat(policy.get("finished_batch_mass_g").asDouble()).isEqualTo(1000.0);
		assertThat(policy.has("maximum_lead_time_days")).isFalse();
		assertThat(policy.has("maximum_purchase_cost_usd")).isFalse();
	}

	@Test
	void clarify_forwards_the_prepared_result_id_and_answers() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<ClarifyBriefRequest> captor = ArgumentCaptor.forClass(ClarifyBriefRequest.class);
		ClarifyBriefResponse clarifyResponse = new ClarifyBriefResponse(
				"rd-clarification-2", "result-1", List.of("request.formula.target_region"),
				JSON.createObjectNode(), JSON.createObjectNode(), 1, true);
		when(perfumeryAiClient.clarifyBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", clarifyResponse, 10L));

		BriefClarifyRequest dto = new BriefClarifyRequest(
				"result-1", Map.of("request.formula.target_region", "EU"), null, null, null, null);
		ClarifyBriefResponse response = service.clarify(REQUEST_ID, MEMBER_ID, dto);

		assertThat(response.previousResultId()).isEqualTo("result-1");
		assertThat(response.stateChanged()).isTrue();
		assertThat(captor.getValue().preparedResultId()).isEqualTo("result-1");
		assertThat(captor.getValue().answers().get("request.formula.target_region").asString()).isEqualTo("EU");
	}

	@Test
	void prepare_checks_project_access_through_the_existing_request_service() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		when(perfumeryAiClient.prepareBrief(any(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		service.prepare(REQUEST_ID, MEMBER_ID, BriefReviewRequest.empty());

		verify(requestService).getAccessibleRequest(eq(REQUEST_ID), eq(MEMBER_ID));
	}

	/** AI 개발팀 확인(2026-09-16): 진단 모드는 prepare 단계부터 최상위에 지정해야 한다. */
	@Test
	void prepare_forwards_the_diagnostic_only_flag_to_the_ai_client() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<PrepareBriefRequest> captor = ArgumentCaptor.forClass(PrepareBriefRequest.class);
		when(perfumeryAiClient.prepareBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		service.prepare(REQUEST_ID, MEMBER_ID, new BriefReviewRequest(null, null, null, null, true));

		assertThat(captor.getValue().diagnosticOnly()).isTrue();
	}

	@Test
	void prepare_defaults_diagnostic_only_to_unset_when_not_requested() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<PrepareBriefRequest> captor = ArgumentCaptor.forClass(PrepareBriefRequest.class);
		when(perfumeryAiClient.prepareBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", readyResponse("review-1"), 10L));

		service.prepare(REQUEST_ID, MEMBER_ID, BriefReviewRequest.empty());

		assertThat(captor.getValue().diagnosticOnly()).isFalse();
	}

	@Test
	void clarify_forwards_the_diagnostic_only_flag_to_the_ai_client() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<ClarifyBriefRequest> captor = ArgumentCaptor.forClass(ClarifyBriefRequest.class);
		ClarifyBriefResponse clarifyResponse = new ClarifyBriefResponse(
				"rd-clarification-2", "result-1", List.of(), JSON.createObjectNode(),
				JSON.createObjectNode(), 1, true);
		when(perfumeryAiClient.clarifyBrief(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", clarifyResponse, 10L));

		BriefClarifyRequest dto = new BriefClarifyRequest("result-1", Map.of("k", "v"), null, null, null, true);
		service.clarify(REQUEST_ID, MEMBER_ID, dto);

		assertThat(captor.getValue().diagnosticOnly()).isTrue();
	}

	private EvaluationResponse diagnosticEvaluationResponse(String reviewId) {
		return new EvaluationResponse("rd-candidates-2", reviewId, JSON.createObjectNode(), "abstained",
				List.of(), List.of(), false, false, true,
				"explicit_public_source_diagnostic_not_operational_recommendation",
				JSON.createObjectNode(), "result-1");
	}

	/** AI 개발팀 확인(2026-09-16): confirmedReviewId는 diagnostic prepare에서 받은 review_id여야 한다. */
	@Test
	void evaluateDiagnostic_forwards_the_confirmed_review_id_and_diagnostic_flag() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<EvaluateFormulaRequest> captor = ArgumentCaptor.forClass(EvaluateFormulaRequest.class);
		when(perfumeryAiClient.evaluateFormula(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", diagnosticEvaluationResponse("review-9"), 10L));

		EvaluateDiagnosticRequest dto = new EvaluateDiagnosticRequest("a".repeat(64), null, null, null);
		EvaluationResponse response = service.evaluateDiagnostic(REQUEST_ID, MEMBER_ID, dto).parsed();

		assertThat(response.isDiagnostic()).isTrue();
		assertThat(response.reviewId()).isEqualTo("review-9");
		assertThat(captor.getValue().confirmedReviewId()).isEqualTo("a".repeat(64));
		assertThat(captor.getValue().diagnosticOnly()).isTrue();
	}

	@Test
	void reassessDiagnostic_forwards_the_fixed_lines_and_confirmed_review_id() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<ReassessFormulaRequest> captor = ArgumentCaptor.forClass(ReassessFormulaRequest.class);
		when(perfumeryAiClient.reassessFormula(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", diagnosticEvaluationResponse("review-10"), 10L));

		ReassessDiagnosticRequest dto = new ReassessDiagnosticRequest(
				"b".repeat(64), List.of(new EvidenceLine("linalyl_acetate", 100.0)), null, null, null);
		EvaluationResponse response = service.reassessDiagnostic(REQUEST_ID, MEMBER_ID, dto).parsed();

		assertThat(response.isDiagnostic()).isTrue();
		assertThat(captor.getValue().confirmedReviewId()).isEqualTo("b".repeat(64));
		assertThat(captor.getValue().lines()).hasSize(1);
		assertThat(captor.getValue().lines().get(0).ingredientId()).isEqualTo("linalyl_acetate");
		assertThat(captor.getValue().diagnosticOnly()).isTrue();
	}

	private StoredCandidate storedCandidate(String candidateId, String backendVersionId) {
		return new StoredCandidate(JSON.createObjectNode(), candidateId, backendVersionId);
	}

	/** compare/revise는 근거 등록 없이도 되므로(AI 확인), BE는 저장 없이 그대로 중계만 한다. */
	@Test
	void compareCandidates_forwards_the_snapshots_without_storing_them() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<CompareCandidatesRequest> captor = ArgumentCaptor.forClass(CompareCandidatesRequest.class);
		CompareCandidatesResponse response = new CompareCandidatesResponse(
				"rd-comparison-2", List.of(JSON.createObjectNode()), List.of(), true, false, 0, false,
				JSON.createObjectNode(), "result-9");
		when(perfumeryAiClient.compareCandidates(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", response, 10L));

		StoredCandidate a = storedCandidate("a".repeat(64), "diagnostic-a");
		StoredCandidate b = storedCandidate("b".repeat(64), "diagnostic-b");
		CompareCandidatesApiRequest dto = new CompareCandidatesApiRequest(List.of(a, b));
		CompareCandidatesResponse result = service.compareCandidates(REQUEST_ID, MEMBER_ID, dto);

		assertThat(result.resultId()).isEqualTo("result-9");
		assertThat(captor.getValue().candidates()).containsExactly(a, b);
	}

	@Test
	void reviseCandidate_forwards_the_source_snapshot_and_instruction() {
		when(requestService.getAccessibleRequest(REQUEST_ID, MEMBER_ID)).thenReturn(fullyStructuredRequest());
		ArgumentCaptor<ReviseCandidateRequest> captor = ArgumentCaptor.forClass(ReviseCandidateRequest.class);
		ReviseCandidateResponse response = new ReviseCandidateResponse(
				"rd-revision-2", JSON.createObjectNode(), JSON.createObjectNode(), JSON.createObjectNode(),
				1, true, "evaluate", "scope");
		when(perfumeryAiClient.reviseCandidate(captor.capture(), any(), any()))
				.thenReturn(new PerfumeryAiResult<>("{}", response, 10L));

		StoredCandidate source = storedCandidate("c".repeat(64), "diagnostic-c");
		ReviseCandidateApiRequest dto = new ReviseCandidateApiRequest(source, "우디 느낌을 더 강하게");
		ReviseCandidateResponse result = service.reviseCandidate(REQUEST_ID, MEMBER_ID, dto);

		assertThat(result.nextOperation()).isEqualTo("evaluate");
		assertThat(captor.getValue().source()).isEqualTo(source);
		assertThat(captor.getValue().instruction()).isEqualTo("우디 느낌을 더 강하게");
	}
}
