package com.perfumeryaicore.global.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import com.perfumeryaicore.global.client.dto.LotionDesignResponse;
import com.perfumeryaicore.global.client.dto.LotionEstimateRequest;
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
				client.designLotion(LotionEstimateRequest.of("citrus lotion", 1, 150.0), "trace-1", null);

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
				client.designLotion(LotionEstimateRequest.of("citrus lotion", 1, 150.0), "trace-1", null);

		assertThat(result.parsed().isUsableCandidate()).isFalse();
		assertThat(result.parsed().searchIncomplete()).isTrue();
	}

	@Test
	void designLotion_response_without_a_status_is_a_schema_mismatch() {
		AtomicInteger calls = new AtomicInteger();
		PerfumeryAiClient client = client(props("wk-a.ws-b", 30, 1), respondWith(HttpStatus.OK, "{}", calls));

		assertThatThrownBy(() -> client.designLotion(
				LotionEstimateRequest.of("citrus lotion", 1, 150.0), "trace-1", null))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
	}
}
