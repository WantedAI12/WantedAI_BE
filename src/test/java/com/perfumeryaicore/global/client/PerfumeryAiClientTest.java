package com.perfumeryaicore.global.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perfumeryaicore.global.client.dto.AiCapabilitiesResponse;
import com.perfumeryaicore.global.client.dto.AssessEvidenceRequest;
import com.perfumeryaicore.global.client.dto.ChangeImpactRequest;
import com.perfumeryaicore.global.client.dto.ClarifyBriefRequest;
import com.perfumeryaicore.global.client.dto.ClarifyBriefResponse;
import com.perfumeryaicore.global.client.dto.CompareCandidatesRequest;
import com.perfumeryaicore.global.client.dto.DiagnosticCandidate;
import com.perfumeryaicore.global.client.dto.EvaluateFormulaRequest;
import com.perfumeryaicore.global.client.dto.EvidenceLine;
import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import com.perfumeryaicore.global.client.dto.LotionDesignResponse;
import com.perfumeryaicore.global.client.dto.LotionEstimateRequest;
import com.perfumeryaicore.global.client.dto.PrepareBriefRequest;
import com.perfumeryaicore.global.client.dto.PrepareBriefResponse;
import com.perfumeryaicore.global.client.dto.ReassessFormulaRequest;
import com.perfumeryaicore.global.client.dto.ReviseCandidateRequest;
import com.perfumeryaicore.global.client.dto.StoredCandidate;
import java.util.List;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

class PerfumeryAiClientTest {

	private static final String OK_FORMULA = """
			{"status":"prototype_ready",
			 "recipe":[{"ingredient_id":"dihydromyrcenol","name":"Dihydromyrcenol","pyramid":"top","concentrate_percent":23.4984}],
			 "temporal_timepoints_minutes":[0,15,60,240,480],
			 "deployment":{"provider":"modal","gpu_required":false}}""";

	private ModalAiProperties props(String token, int rpm, int retries) {
		return new ModalAiProperties("http://ai.local", token,
				Duration.ofSeconds(1), Duration.ofSeconds(2), rpm, retries);
	}

	private PerfumeryAiClient client(ModalAiProperties props, ExchangeFunction exchange) {
		WebClient webClient = WebClient.builder().baseUrl(props.baseUrl()).exchangeFunction(exchange).build();
		return new PerfumeryAiClient(webClient, props);
	}

	private static ExchangeFunction respondWith(HttpStatus status, String body, AtomicInteger counter) {
		return request -> {
			counter.incrementAndGet();
			return Mono.just(ClientResponse.create(status)
					.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
					.body(body)
					.build());
		};
	}

	@Test
	void generateFormula_success_preserves_raw_and_parses_view() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.OK, OK_FORMULA, calls));

		PerfumeryAiResult<FormulaGenerationResponse> result = client.generateFormula(
				FormulaGenerationRequest.standard("citrus woody", "EU", "eau_de_parfum", null, null, 12), "trace-1");

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.rawJson()).contains("\"status\":\"prototype_ready\"");
		assertThat(result.parsed().status()).isEqualTo("prototype_ready");
		assertThat(result.parsed().recipeSize()).isEqualTo(1);
		assertThat(result.parsed().temporalTimepointsMinutes()).containsExactly(0, 15, 60, 240, 480);
		assertThat(result.parsed().deployment().provider()).isEqualTo("modal");
		assertThat(result.latencyMillis()).isGreaterThanOrEqualTo(0);
	}

	@Test
	void missing_token_fails_as_server_config_error_without_calling() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("", 30, 1),
				respondWith(HttpStatus.OK, OK_FORMULA, calls));

		assertThatThrownBy(() -> client.generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_AUTH_MISCONFIGURED);
		assertThat(calls.get()).isZero();
	}

	@Test
	void unauthorized_maps_to_config_error_and_does_not_retry() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.UNAUTHORIZED, "{}", calls));

		assertThatThrownBy(() -> client.generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_AUTH_MISCONFIGURED);
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void rate_limited_response_retries_once_then_fails() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.TOO_MANY_REQUESTS, "{}", calls));

		assertThatThrownBy(() -> client.generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
		assertThat(calls.get()).isEqualTo(2);
	}

	@Test
	void server_error_retries_once_then_fails() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.BAD_GATEWAY, "{}", calls));

		assertThatThrownBy(() -> client.generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_SERVICE_ERROR);
		assertThat(calls.get()).isEqualTo(2);
	}

	/**
	 * BE-108(AI/프론트 개발팀 확인, 2026-09-17): 422 같은 4xx 입력 검증 실패는 같은 요청을
	 * 다시 보내도 똑같이 실패한다 - 5xx 일시 오류와 같은 AI_SERVICE_ERROR 코드를 쓰더라도
	 * 재시도하면 안 된다는 신호(retryable=false)를 예외가 직접 실어 날라야 한다. 이게 없으면
	 * JobExecutor가 오류 코드만 보고 재시도 가능으로 오판해 영원히 실패할 작업을 계속
	 * 재시도한다.
	 */
	@Test
	void a_4xx_validation_rejection_is_not_retried_and_carries_a_non_retryable_signal() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.UNPROCESSABLE_ENTITY, "{\"detail\":\"missing required field\"}", calls));

		assertThatThrownBy(() -> client.generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode", "retryable")
				.containsExactly(ErrorCode.AI_SERVICE_ERROR, false);
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void local_rate_limit_blocks_call_beyond_cap() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 2, 0),
				respondWith(HttpStatus.OK, "{\"reference_molecules\":29240}", calls));

		client.catalogRaw("t1");
		client.catalogRaw("t2");
		assertThatThrownBy(() -> client.catalogRaw("t3"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
		assertThat(calls.get()).isEqualTo(2);
	}

	@Test
	void schema_mismatch_when_ready_status_has_empty_recipe() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.OK, "{\"status\":\"prototype_ready\",\"recipe\":[]}", calls));

		assertThatThrownBy(() -> client.generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
	}

	/** BE-048: 게이트 통과 콜백은 재시도 횟수와 무관하게 호출당 정확히 한 번만 실행된다. */
	@Test
	void slot_acquired_callback_fires_once_per_call_even_across_retries() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.TOO_MANY_REQUESTS, "{}", calls));
		AtomicInteger callbackCount = new AtomicInteger();

		assertThatThrownBy(() -> client.generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t",
				callbackCount::incrementAndGet))
				.isInstanceOf(BusinessException.class);

		assertThat(calls.get()).isEqualTo(2); // 원래 시도 + 재시도 1회
		assertThat(callbackCount.get()).isEqualTo(1); // 콜백은 게이트를 통과한 시점에 한 번뿐
	}

	@Test
	void no_safe_match_with_empty_recipe_is_accepted() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.OK, "{\"status\":\"no_safe_match\",\"recipe\":[]}", calls));

		PerfumeryAiResult<FormulaGenerationResponse> result = client.generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t");

		assertThat(result.parsed().isNoSafeMatch()).isTrue();
		assertThat(result.parsed().recipeSize()).isZero();
	}

	@Test
	void designLotion_success_preserves_raw_and_parses_view() {
		AtomicInteger calls = new AtomicInteger();
		String okLotion = """
				{"status":"ready","profile_target_met":true,
				 "recipe":[{"ingredient_id":"citral","name":"Citral","concentrate_percent":1.2}],
				 "closest_candidate":[]}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, okLotion, calls));

		PerfumeryAiResult<LotionDesignResponse> result =
				client.designLotion(LotionEstimateRequest.of("citrus lotion", 1, 150.0, 2.0), "trace-1", null);

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.parsed().status()).isEqualTo("ready");
		assertThat(result.parsed().isUsableCandidate()).isTrue();
		assertThat(result.parsed().recipeSize()).isEqualTo(1);
	}

	/** 팀 확인: recipe/closest_candidate가 있어도 profile_target_met이 거짓이면 정상 후보가 아니다. */
	@Test
	void designLotion_with_unmet_profile_target_is_not_a_usable_candidate() {
		AtomicInteger calls = new AtomicInteger();
		String rejected = """
				{"status":"insufficient_observed_target_coverage","profile_target_met":false,
				 "search_incomplete":true,"recipe":[],"closest_candidate":[]}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, rejected, calls));

		PerfumeryAiResult<LotionDesignResponse> result =
				client.designLotion(LotionEstimateRequest.of("citrus lotion", 1, 150.0, 2.0), "trace-1", null);

		assertThat(result.parsed().isUsableCandidate()).isFalse();
		assertThat(result.parsed().searchIncomplete()).isTrue();
	}

	@Test
	void designLotion_response_without_a_status_is_a_schema_mismatch() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, "{}", calls));

		assertThatThrownBy(() -> client.designLotion(
				LotionEstimateRequest.of("citrus lotion", 1, 150.0, 2.0), "trace-1", null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void prepareBrief_needs_input_status_carries_questions() {
		AtomicInteger calls = new AtomicInteger();
		String needsInput = """
				{"schema_version":"rd-brief-2","status":"needs_input",
				 "missing_fields":["request.formula.target_region"],
				 "conflicting_fields":[],
				 "questions":[{"id":"request.formula.target_region","field":"request.formula.target_region",
				               "required":true,"input_type":"choice","options":[{"value":"EU","label":"EU"}],
				               "reason_code":"explicit_value_missing","question":"대상 지역를 확인해 주세요."}],
				 "review_id":null,"confirmation_required":true,"recipe_generated":false,
				 "result_id":"result-1"}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, needsInput, calls));
		PrepareBriefRequest request = PrepareBriefRequest.of(
				JSON.createObjectNode(), JSON.createObjectNode());

		var result = client.prepareBrief(request, "trace-1", null);

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.parsed().needsInput()).isTrue();
		assertThat(result.parsed().questions()).hasSize(1);
		assertThat(result.parsed().questions().get(0).id()).isEqualTo("request.formula.target_region");
		assertThat(result.parsed().resultId()).isEqualTo("result-1");
		assertThat(result.parsed().reviewId()).isNull();
	}

	@Test
	void prepareBrief_ready_status_carries_a_review_id() {
		AtomicInteger calls = new AtomicInteger();
		String ready = """
				{"schema_version":"rd-brief-2","status":"ready","missing_fields":[],"conflicting_fields":[],
				 "questions":[],"review_id":"review-1","confirmation_required":true,
				 "recipe_generated":false,"result_id":"result-2"}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, ready, calls));
		PrepareBriefRequest request = PrepareBriefRequest.of(
				JSON.createObjectNode(), JSON.createObjectNode());

		var result = client.prepareBrief(request, "trace-1", null);

		assertThat(result.parsed().isReady()).isTrue();
		assertThat(result.parsed().reviewId()).isEqualTo("review-1");
	}

	@Test
	void clarifyBrief_success_preserves_applied_answer_ids() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"schema_version":"rd-clarification-2","previous_result_id":"result-1",
				 "applied_answer_ids":["request.formula.target_region"],
				 "request":{},"prepared":{"status":"ready"},"new_inference_count":1,"state_changed":true}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, response, calls));
		ClarifyBriefRequest request = ClarifyBriefRequest.of(
				JSON.createObjectNode(), JSON.createObjectNode(), "result-1", JSON.createObjectNode());

		var result = client.clarifyBrief(request, "trace-1", null);

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.parsed().previousResultId()).isEqualTo("result-1");
		assertThat(result.parsed().appliedAnswerIds()).containsExactly("request.formula.target_region");
		assertThat(result.parsed().stateChanged()).isTrue();
	}

	@Test
	void prepareBrief_server_error_retries_once_then_fails() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.BAD_GATEWAY, "{}", calls));
		PrepareBriefRequest request = PrepareBriefRequest.of(
				JSON.createObjectNode(), JSON.createObjectNode());

		assertThatThrownBy(() -> client.prepareBrief(request, "trace-1", null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_SERVICE_ERROR);
		assertThat(calls.get()).isEqualTo(2);
	}

	@Test
	void evidenceStatus_success_parses_top_level_flags() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"schema_version":"rd-evidence-status-1","contract":{},
				 "operator_bundle_registered":false,"public_sources_registered":true,
				 "public_source_diagnostics_available":true,"default_operational_gate_bypassed":false,
				 "coverage":{"active_material_count":3830}}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, response, calls));

		var result = client.evidenceStatus("trace-1");

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.parsed().publicSourcesRegistered()).isTrue();
		assertThat(result.parsed().operatorBundleRegistered()).isFalse();
	}

	@Test
	void evidenceCoverage_success_parses_pagination_fields() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"active_material_count":3830,"public_price_connected_count":243,
				 "no_public_price_count":3587,"operationally_complete_count":0,
				 "source_products_examined":1212,"scope":"all_active_materials_missing_entries_preserved",
				 "offset":0,"limit":100,"items":[{"ingredient_id":"aldehyde_c10"}],"has_more":true}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, response, calls));

		var result = client.evidenceCoverage(0, 100, "trace-1");

		assertThat(result.parsed().items()).hasSize(1);
		assertThat(result.parsed().hasMore()).isTrue();
		assertThat(result.parsed().activeMaterialCount()).isEqualTo(3830);
	}

	@Test
	void assessEvidence_blocked_status_carries_blockers() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"schema_version":"rd-evidence-assessment-1","status":"blocked","gate_passed":false,
				 "blockers":[{"ingredient_id":"linalyl_acetate","reason":"missing_scoped_evidence"}],
				 "manufacturing_approval":false,"result_id":"result-9"}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, response, calls));
		AssessEvidenceRequest request = new AssessEvidenceRequest(
				List.of(new EvidenceLine("linalyl_acetate", 100.0)), "EU", "eau_de_parfum", 15.0, 180.0, null,
				JSON.createObjectNode());

		var result = client.assessEvidence(request, "trace-1", null);

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.parsed().isBlocked()).isTrue();
		assertThat(result.parsed().blockers()).hasSize(1);
		assertThat(result.parsed().blockers().get(0).ingredientId()).isEqualTo("linalyl_acetate");
	}

	@Test
	void changeImpact_success_parses_the_real_v89_schema() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"schema_version":"rd-change-impact-1","before":{},"after":{},"changes":{},
				 "affected_material_count":1,"review_required":true,"state_changed":false,
				 "manufacturing_approval":false,
				 "scope":"public_observation_comparison_not_operator_approval_or_inventory_reservation",
				 "result_id":"result-1","contract":{}}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, response, calls));
		ChangeImpactRequest request = new ChangeImpactRequest(
				List.of(new EvidenceLine("linalyl_acetate", 100.0)), "EU", "eau_de_parfum", 15.0, 180.0, null,
				JSON.createObjectNode(), "public-all-20260914T021125");

		var result = client.changeImpact(request, "trace-1", null);

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.parsed().affectedMaterialCount()).isEqualTo(1);
		assertThat(result.parsed().reviewRequired()).isTrue();
	}

	/** AI팀 확인(2026-09-16): 비교할 근거 자료가 전혀 없으면 이 구조화된 422로 거부된다. */
	@Test
	void changeImpact_rejects_with_a_specific_error_when_evidence_snapshots_are_missing() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"detail":{"status":"abstained","code":"EVIDENCE_SNAPSHOTS_MISSING"}}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.UNPROCESSABLE_ENTITY, response, calls));
		ChangeImpactRequest request = new ChangeImpactRequest(
				List.of(new EvidenceLine("linalyl_acetate", 100.0)), "EU", "eau_de_parfum", 15.0, 180.0, null,
				JSON.createObjectNode(), "public-all-20260914T021125");

		assertThatThrownBy(() -> client.changeImpact(request, "trace-1", null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CHANGE_IMPACT_EVIDENCE_MISSING);
	}

	/** 근거 없음 외의 다른 422(예: 잘못된 버전 문자열)는 대부분 null인 "성공"으로 잘못 파싱되면 안 된다. */
	@Test
	void changeImpact_rejects_other_422_bodies_as_a_generic_ai_error() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"detail":{"status":"error","code":"UNKNOWN_PUBLIC_OBSERVATION_VERSION"}}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.UNPROCESSABLE_ENTITY, response, calls));
		ChangeImpactRequest request = new ChangeImpactRequest(
				List.of(new EvidenceLine("linalyl_acetate", 100.0)), "EU", "eau_de_parfum", 15.0, 180.0, null,
				JSON.createObjectNode(), "audit-missing");

		assertThatThrownBy(() -> client.changeImpact(request, "trace-1", null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_SERVICE_ERROR);
	}

	@Test
	void capabilities_success_parses_supported_product_codes() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"schema_version":"ai-capabilities-1",
				 "supported_product_codes":["eau_de_parfum","body_wash"],
				 "physical_property_evidence":{},"integration_contract":{},"scope":"x"}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, response, calls));

		AiCapabilitiesResponse result = client.capabilities();

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.supportedProductCodes()).containsExactly("eau_de_parfum", "body_wash");
	}

	@Test
	void capabilities_does_not_consume_the_local_rate_limit_cap() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 1, 0),
				respondWith(HttpStatus.OK, "{\"schema_version\":\"ai-capabilities-1\"}", calls));

		client.catalogRaw("t1");
		// catalogRaw already consumed the 1/min cap; capabilities must still succeed since it bypasses the gate.
		AiCapabilitiesResponse result = client.capabilities();

		assertThat(result.schemaVersion()).isEqualTo("ai-capabilities-1");
		assertThat(calls.get()).isEqualTo(2);
	}

	/** AI 개발팀 확인(2026-09-16): diagnostic_only=true는 근거 미등록 상태에서도 HTTP 200, 결과는 diagnostic_candidates에 담긴다. */
	@Test
	void evaluateFormula_diagnostic_success_parses_diagnostic_candidates_not_the_recommendation_list() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"schema_version":"rd-candidates-2","review_id":"review-1","contract":{},
				 "status":"abstained","candidates":[],
				 "diagnostic_candidates":[{"candidate_id":"c1","formula_id":"f1","status":"abstained",
				   "assessment_kind":"generated_candidate","target_match_score":91.11,
				   "target_match_unit":"model_points_0_100","product_concentration_percent":15.0,
				   "material_count":21,"recommendation_allowed":false,
				   "result":{"status":"no_safe_match","recipe":[],"closest_candidate":[{"ingredient_id":"x"}]},
				   "rd_gates":{"registered_evidence":false,"existing_safety":true,
				               "scientific_domain":false,"profile_and_persistence":false}}],
				 "state_changed":false,"manufacturing_approval":false,"diagnostic_only":true,
				 "scope":"explicit_public_source_diagnostic_not_operational_recommendation",
				 "input_snapshot":{},"result_id":"result-1"}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, response, calls));
		EvaluateFormulaRequest request = EvaluateFormulaRequest.diagnostic(
				JSON.createObjectNode(), JSON.createObjectNode(), "a".repeat(64));

		var result = client.evaluateFormula(request, "trace-1", null);

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.parsed().isDiagnostic()).isTrue();
		assertThat(result.parsed().candidates()).isEmpty();
		assertThat(result.parsed().diagnosticCandidates()).hasSize(1);
		DiagnosticCandidate candidate = result.parsed().diagnosticCandidates().get(0);
		assertThat(candidate.isRecommendationAllowed()).isFalse();
		assertThat(candidate.result().get("status").asString()).isEqualTo("no_safe_match");
		assertThat(candidate.result().get("recipe").isEmpty()).isTrue();
	}

	@Test
	void evaluateFormula_server_error_retries_once_then_fails() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1),
				respondWith(HttpStatus.BAD_GATEWAY, "{}", calls));
		EvaluateFormulaRequest request = EvaluateFormulaRequest.diagnostic(
				JSON.createObjectNode(), JSON.createObjectNode(), "a".repeat(64));

		assertThatThrownBy(() -> client.evaluateFormula(request, "trace-1", null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_SERVICE_ERROR);
		assertThat(calls.get()).isEqualTo(2);
	}

	@Test
	void reassessFormula_diagnostic_success_parses_diagnostic_candidates() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"schema_version":"rd-candidates-2","review_id":"review-2","contract":{},
				 "status":"abstained","candidates":[],
				 "diagnostic_candidates":[{"candidate_id":"c2","formula_id":"f2","status":"abstained",
				   "assessment_kind":"fixed_formula","recommendation_allowed":false,
				   "result":{"status":"no_safe_match","recipe":[],"closest_candidate":[]}}],
				 "state_changed":false,"manufacturing_approval":false,"diagnostic_only":true,
				 "scope":"explicit_public_source_diagnostic_not_operational_recommendation",
				 "input_snapshot":{},"result_id":"result-2"}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, response, calls));
		ReassessFormulaRequest request = ReassessFormulaRequest.diagnostic(
				JSON.createObjectNode(), JSON.createObjectNode(),
				List.of(new EvidenceLine("linalyl_acetate", 100.0)), "a".repeat(64));

		var result = client.reassessFormula(request, "trace-1", null);

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.parsed().isDiagnostic()).isTrue();
		assertThat(result.parsed().diagnosticCandidates()).hasSize(1);
		assertThat(result.parsed().diagnosticCandidates().get(0).isRecommendationAllowed()).isFalse();
	}

	@Test
	void compareCandidates_success_parses_top_level_fields() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"schema_version":"rd-comparison-2","candidates":[{"candidate_id":"c1"}],"pairs":[],
				 "automatic_ranking_performed":true,"recommendation_decision_performed":false,
				 "new_inference_count":0,"state_changed":false,"provenance":{},"result_id":"result-9"}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, response, calls));
		StoredCandidate a = new StoredCandidate(JSON.createObjectNode(), "candidate-1", "backend-1");
		StoredCandidate b = new StoredCandidate(JSON.createObjectNode(), "candidate-2", "backend-2");
		CompareCandidatesRequest request = new CompareCandidatesRequest(List.of(a, b));

		var result = client.compareCandidates(request, "trace-1", null);

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.parsed().candidates()).hasSize(1);
		assertThat(result.parsed().automaticRankingPerformed()).isTrue();
		assertThat(result.parsed().resultId()).isEqualTo("result-9");
	}

	@Test
	void reviseCandidate_success_parses_next_operation() {
		AtomicInteger calls = new AtomicInteger();
		String response = """
				{"schema_version":"rd-revision-2","request":{},"prepared":{},"adjustments":{},
				 "new_inference_count":1,"state_changed":true,"next_operation":"evaluate","scope":"x"}""";
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, response, calls));
		StoredCandidate source = new StoredCandidate(JSON.createObjectNode(), "candidate-1", "backend-1");
		ReviseCandidateRequest request = new ReviseCandidateRequest(source, "우디 느낌을 더 강하게");

		var result = client.reviseCandidate(request, "trace-1", null);

		assertThat(calls.get()).isEqualTo(1);
		assertThat(result.parsed().nextOperation()).isEqualTo("evaluate");
		assertThat(result.parsed().stateChanged()).isTrue();
	}
}
