package com.perfumeryaicore.global.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import com.perfumeryaicore.global.exception.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 실제 HTTP 서버로 검증한다: Modal 웹 엔드포인트는 오래 걸리는 요청(약 150초 초과)을 303으로 결과 조회
 * 주소에 넘긴다. 이를 따라가지 않으면 본문이 빈 응답을 성공으로 받아 후보 생성이 'content is null'로
 * 실패한다. 가짜 ExchangeFunction으로는 실제 네트워크 클라이언트의 리다이렉트 동작을 검증할 수 없다.
 */
class PerfumeryAiClientRedirectTest {

	private static final String OK_FORMULA = """
			{"status":"prototype_ready",
			 "recipe":[{"ingredient_id":"dihydromyrcenol","name":"Dihydromyrcenol","pyramid":"top","concentrate_percent":23.4984}],
			 "temporal_timepoints_minutes":[0,15,60,240,480],
			 "deployment":{"provider":"modal","gpu_required":false}}""";

	private HttpServer server;
	private final List<String> seenRequests = new CopyOnWriteArrayList<>();
	private final AtomicInteger polls = new AtomicInteger();

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	private String baseUrl() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	private PerfumeryAiClient client() {
		ModalAiProperties props = new ModalAiProperties(baseUrl(), "wk-a.ws-b",
				Duration.ofSeconds(2), Duration.ofSeconds(10), 30, 0, 1350.0);
		WebClient webClient = new PerfumeryAiClientConfig().perfumeryAiWebClient(props);
		return new PerfumeryAiClient(webClient, props);
	}

	private void record(HttpExchange exchange) {
		seenRequests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
				+ " auth=" + exchange.getRequestHeaders().getFirst("Authorization"));
	}

	private static void respond(HttpExchange exchange, int status, String body, String location) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		if (location != null) {
			exchange.getResponseHeaders().add("Location", location);
		}
		exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			exchange.getResponseBody().write(bytes);
		}
		exchange.close();
	}

	@Test
	void a_303_to_the_same_host_is_followed_until_the_result_is_ready_and_keeps_the_auth_header() {
		server.createContext("/v1/formulas", exchange -> {
			record(exchange);
			respond(exchange, 303, "", "/v1/formulas/result?call=fc-1");
		});
		server.createContext("/v1/formulas/result", exchange -> {
			record(exchange);
			// 아직 계산 중이면 다시 303, 끝나면 200 - 결과 조회를 반복하는 Modal의 방식
			if (polls.incrementAndGet() < 2) {
				respond(exchange, 303, "", "/v1/formulas/result?call=fc-1");
			} else {
				respond(exchange, 200, OK_FORMULA, null);
			}
		});
		server.start();

		var result = client().generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t");

		FormulaGenerationResponse parsed = result.parsed();
		assertThat(parsed.status()).isEqualTo("prototype_ready");
		assertThat(parsed.recipeSize()).isEqualTo(1);
		assertThat(seenRequests).hasSize(3);
		assertThat(seenRequests.get(0)).startsWith("POST /v1/formulas ");
		assertThat(seenRequests.get(1)).startsWith("GET /v1/formulas/result?call=fc-1 ");
		// 결과 조회 요청에도 인증 헤더가 그대로 실려야 한다
		assertThat(seenRequests).allMatch(r -> r.endsWith("auth=Bearer wk-a.ws-b"));
	}

	/** 인증 토큰이 다른 호스트로 새지 않도록, 다른 호스트로 가는 303은 따라가지 않는다. */
	@Test
	void a_303_to_another_host_is_not_followed_and_the_token_is_never_sent_there() {
		AtomicInteger otherHostHits = new AtomicInteger();
		server.createContext("/v1/formulas", exchange -> {
			record(exchange);
			respond(exchange, 303, "", "http://localhost:" + server.getAddress().getPort() + "/elsewhere");
		});
		server.createContext("/elsewhere", exchange -> {
			otherHostHits.incrementAndGet();
			respond(exchange, 200, OK_FORMULA, null);
		});
		server.start();

		assertThatThrownBy(() -> client().generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("빈 응답");
		assertThat(otherHostHits.get()).isZero();
	}

	@Test
	void other_redirect_statuses_are_not_followed() {
		server.createContext("/v1/formulas", exchange -> {
			record(exchange);
			respond(exchange, 302, "", "/v1/formulas/moved");
		});
		server.createContext("/v1/formulas/moved", exchange -> {
			record(exchange);
			respond(exchange, 200, OK_FORMULA, null);
		});
		server.start();

		assertThatThrownBy(() -> client().generateFormula(
				FormulaGenerationRequest.standard("x", "EU", "eau_de_parfum", null, null, 12), "t"))
				.isInstanceOf(BusinessException.class);
		assertThat(seenRequests).hasSize(1);
	}
}
