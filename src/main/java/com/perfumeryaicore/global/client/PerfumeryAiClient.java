package com.perfumeryaicore.global.client;

import com.perfumeryaicore.global.client.dto.AiHealthResponse;
import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 조향 AI(Modal) 서버-투-서버 클라이언트.
 *
 * <p>안내 사항 반영:
 * <ul>
 *   <li>동시 실행 1개로 직렬화(세마포어). 여러 요청은 큐에서 순서대로 처리한다.</li>
 *   <li>분당 호출 상한을 카운트하고, 초과 시 {@link ErrorCode#AI_RATE_LIMIT_EXCEEDED}로 반환한다.</li>
 *   <li>429·5xx·타임아웃은 짧은 대기 후 최대 {@code maxRetries}회만 재시도한다.
 *       인증 오류·기타 4xx는 재시도하지 않는다.</li>
 *   <li>인증 실패는 사용자 입력 문제가 아니라 서버 설정 오류로 기록한다. 로그에 토큰 원문을 남기지 않는다.</li>
 *   <li>응답 원문 전체는 {@link PerfumeryAiResult#rawJson()}에 보존하고 농도값을 재보정하지 않는다.</li>
 * </ul>
 */
@Slf4j
@Component
public class PerfumeryAiClient {

	private static final long RATE_WINDOW_MILLIS = 60_000L;
	private static final long RETRY_BACKOFF_MILLIS = 1_500L;
	private static final List<Integer> EXPECTED_TIMEPOINTS = List.of(0, 15, 60, 240, 480);

	private final WebClient webClient;
	private final ModalAiProperties properties;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private final Semaphore concurrencyGate = new Semaphore(1, true);
	private final Deque<Long> recentCallMillis = new ArrayDeque<>();

	public PerfumeryAiClient(WebClient perfumeryAiWebClient, ModalAiProperties properties) {
		this.webClient = perfumeryAiWebClient;
		this.properties = properties;
	}

	/** 서버 상태·Wheel·registry 확인. 운영 헬스체크 용도(게이트·레이트 리밋 없음). */
	public AiHealthResponse health() {
		requireAuthToken("health", "-");
		String body = withRetry("health", "-", () -> webClient.get().uri("/health")
				.retrieve().bodyToMono(String.class).block(blockTimeout()));
		return parse(body, AiHealthResponse.class);
	}

	/** 원료 카탈로그 원문. 로컬 미러 동기화가 원본을 그대로 보관할 수 있도록 문자열로 반환한다. */
	public String catalogRaw(String traceId) {
		return serializedCall("catalog", traceId, () -> webClient.get().uri("/v1/catalog")
				.retrieve().bodyToMono(String.class).block(blockTimeout()));
	}

	/**
	 * 자연어 brief와 제약을 정량 조향식으로 변환한다.
	 *
	 * @param request Modal 스키마에 맞춘 요청 (정의되지 않은 필드 추가 금지)
	 * @param traceId 사용자 요청·AI 호출·결과를 같은 로그 흐름으로 잇는 내부 식별자
	 */
	public PerfumeryAiResult generateFormula(FormulaGenerationRequest request, String traceId) {
		long start = System.currentTimeMillis();
		String body = serializedCall("formulas", traceId, () -> webClient.post().uri("/v1/formulas")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve().bodyToMono(String.class).block(blockTimeout()));
		long latency = System.currentTimeMillis() - start;

		FormulaGenerationResponse parsed = parse(body, FormulaGenerationResponse.class);
		verifyFormulaShape(parsed, traceId);
		return new PerfumeryAiResult(body, parsed, latency);
	}

	// --- 호출 파이프라인: 인증 확인 → 동시성 게이트 → 레이트 리밋 → 재시도 ---

	private String serializedCall(String op, String traceId, Supplier<String> call) {
		requireAuthToken(op, traceId);
		acquireGate(op, traceId);
		try {
			throttle(op, traceId);
			return withRetry(op, traceId, call);
		} finally {
			concurrencyGate.release();
		}
	}

	private String withRetry(String op, String traceId, Supplier<String> call) {
		int attempt = 0;
		while (true) {
			attempt++;
			long start = System.currentTimeMillis();
			try {
				String result = execute(call);
				log.info("[AI] op={} trace={} attempt={} result=OK durationMs={}",
						op, traceId, attempt, System.currentTimeMillis() - start);
				return result;
			} catch (AiCallException e) {
				long durationMs = System.currentTimeMillis() - start;
				if (e.retryable && attempt <= properties.maxRetries()) {
					log.warn("[AI] op={} trace={} attempt={} error={} durationMs={} -> retry",
							op, traceId, attempt, e.errorCode.name(), durationMs);
					sleep(RETRY_BACKOFF_MILLIS * attempt);
					continue;
				}
				log.error("[AI] op={} trace={} attempt={} error={} durationMs={} retryable={}",
						op, traceId, attempt, e.errorCode.name(), durationMs, e.retryable);
				throw new BusinessException(e.errorCode);
			}
		}
	}

	/** WebClient 호출을 실행하고 발생 예외를 {@link AiCallException}(재시도 여부 포함)으로 변환한다. */
	private String execute(Supplier<String> call) {
		try {
			return call.get();
		} catch (WebClientResponseException e) {
			int status = e.getStatusCode().value();
			if (status == 401 || status == 403) {
				throw new AiCallException(ErrorCode.AI_AUTH_MISCONFIGURED, false);
			}
			if (status == 429) {
				throw new AiCallException(ErrorCode.AI_RATE_LIMIT_EXCEEDED, true);
			}
			if (e.getStatusCode().is5xxServerError()) {
				throw new AiCallException(ErrorCode.AI_SERVICE_ERROR, true);
			}
			throw new AiCallException(ErrorCode.AI_SERVICE_ERROR, false);
		} catch (WebClientRequestException e) {
			// 연결 실패·네트워크 오류·응답 타임아웃
			throw new AiCallException(ErrorCode.AI_SERVICE_TIMEOUT, true);
		} catch (IllegalStateException e) {
			// Mono#block 대기 시간 초과
			throw new AiCallException(ErrorCode.AI_SERVICE_TIMEOUT, true);
		}
	}

	private void requireAuthToken(String op, String traceId) {
		if (!properties.hasAuthToken()) {
			log.error("[AI] op={} trace={} error=AI_AUTH_MISCONFIGURED reason=missing-token", op, traceId);
			throw new BusinessException(ErrorCode.AI_AUTH_MISCONFIGURED);
		}
	}

	private void acquireGate(String op, String traceId) {
		try {
			concurrencyGate.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("[AI] op={} trace={} error=interrupted-waiting-for-slot", op, traceId);
			throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
		}
	}

	private synchronized void throttle(String op, String traceId) {
		long now = System.currentTimeMillis();
		long windowStart = now - RATE_WINDOW_MILLIS;
		while (!recentCallMillis.isEmpty() && recentCallMillis.peekFirst() < windowStart) {
			recentCallMillis.pollFirst();
		}
		if (recentCallMillis.size() >= properties.requestsPerMinute()) {
			log.warn("[AI] op={} trace={} error=AI_RATE_LIMIT_EXCEEDED windowCount={}",
					op, traceId, recentCallMillis.size());
			throw new BusinessException(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
		}
		recentCallMillis.addLast(now);
	}

	private void verifyFormulaShape(FormulaGenerationResponse parsed, String traceId) {
		if (parsed.status() == null) {
			throw new BusinessException(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
		}
		if (parsed.isNoSafeMatch()) {
			return; // recipe=[] 정상 응답
		}
		if (parsed.recipeSize() == 0 || parsed.temporalTimepointsMinutes() == null) {
			throw new BusinessException(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
		}
		if (!EXPECTED_TIMEPOINTS.equals(parsed.temporalTimepointsMinutes())) {
			log.warn("[AI] op=formulas trace={} unexpected timepoints={}",
					traceId, parsed.temporalTimepointsMinutes());
		}
	}

	private <T> T parse(String body, Class<T> type) {
		try {
			return jsonMapper.readValue(body, type);
		} catch (JacksonException e) {
			log.error("[AI] response parse failure type={} reason={}", type.getSimpleName(), e.getMessage());
			throw new BusinessException(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
		}
	}

	private Duration blockTimeout() {
		return properties.responseTimeout().plusSeconds(5);
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static final class AiCallException extends RuntimeException {

		private final transient ErrorCode errorCode;
		private final boolean retryable;

		private AiCallException(ErrorCode errorCode, boolean retryable) {
			this.errorCode = errorCode;
			this.retryable = retryable;
		}
	}
}
