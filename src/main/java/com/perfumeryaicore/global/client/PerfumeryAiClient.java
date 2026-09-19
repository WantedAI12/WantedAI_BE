package com.perfumeryaicore.global.client;

import com.perfumeryaicore.global.client.dto.AiCapabilitiesResponse;
import com.perfumeryaicore.global.client.dto.AiHealthResponse;
import com.perfumeryaicore.global.client.dto.AssessEvidenceRequest;
import com.perfumeryaicore.global.client.dto.AssessEvidenceResponse;
import com.perfumeryaicore.global.client.dto.ChangeImpactRequest;
import com.perfumeryaicore.global.client.dto.ChangeImpactResponse;
import com.perfumeryaicore.global.client.dto.ClarifyBriefRequest;
import com.perfumeryaicore.global.client.dto.ClarifyBriefResponse;
import com.perfumeryaicore.global.client.dto.CompareCandidatesRequest;
import com.perfumeryaicore.global.client.dto.CompareCandidatesResponse;
import com.perfumeryaicore.global.client.dto.EvaluateFormulaRequest;
import com.perfumeryaicore.global.client.dto.EvaluationResponse;
import com.perfumeryaicore.global.client.dto.EvidenceCoverageResponse;
import com.perfumeryaicore.global.client.dto.EvidenceStatusResponse;
import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import com.perfumeryaicore.global.client.dto.LotionDesignResponse;
import com.perfumeryaicore.global.client.dto.LotionEstimateRequest;
import com.perfumeryaicore.global.client.dto.PrepareBriefRequest;
import com.perfumeryaicore.global.client.dto.PrepareBriefResponse;
import com.perfumeryaicore.global.client.dto.ReassessFormulaRequest;
import com.perfumeryaicore.global.client.dto.ReviseCandidateRequest;
import com.perfumeryaicore.global.client.dto.ReviseCandidateResponse;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
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

	/**
	 * 지원 제품군·연산별 등록/가동 여부·모델 버전 정보. {@code /health}와 같은 이유로 게이트·레이트
	 * 리밋 없이 호출한다 - 실제 조향 연산이 아니라 런타임 메타데이터 조회다.
	 */
	public AiCapabilitiesResponse capabilities() {
		requireAuthToken("capabilities", "-");
		String body = withRetry("capabilities", "-", () -> webClient.get().uri("/v1/ai/capabilities")
				.retrieve().bodyToMono(String.class).block(blockTimeout()));
		return parse(body, AiCapabilitiesResponse.class);
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
		return catalogRaw(traceId, null);
	}

	/**
	 * @param onSlotAcquired 동시성 게이트를 실제로 통과한 직후(대기열 통과 후) 정확히 한 번 호출된다.
	 *     재시도가 여러 번 일어나도 게이트를 다시 통과하지 않으므로 한 번만 호출된다(BE-048).
	 */
	public String catalogRaw(String traceId, Runnable onSlotAcquired) {
		return serializedCall("catalog", traceId, onSlotAcquired, () -> webClient.get().uri("/v1/catalog")
				.retrieve().bodyToMono(String.class).block(blockTimeout()));
	}

	/**
	 * 자연어 brief와 제약을 정량 조향식으로 변환한다.
	 *
	 * @param request Modal 스키마에 맞춘 요청 (정의되지 않은 필드 추가 금지)
	 * @param traceId 사용자 요청·AI 호출·결과를 같은 로그 흐름으로 잇는 내부 식별자
	 */
	public PerfumeryAiResult<FormulaGenerationResponse> generateFormula(FormulaGenerationRequest request, String traceId) {
		return generateFormula(request, traceId, null);
	}

	/**
	 * @param onSlotAcquired 동시성 게이트를 실제로 통과한 직후(BE-048) 정확히 한 번 호출된다. 그 시점부터
	 *     지연시간을 측정하므로, 다른 호출이 먼저 게이트를 쓰고 있어 대기한 시간은 지연시간에 섞이지 않는다.
	 */
	public PerfumeryAiResult<FormulaGenerationResponse> generateFormula(
			FormulaGenerationRequest request, String traceId, Runnable onSlotAcquired) {
		AtomicLong startedAt = new AtomicLong();
		Runnable markStart = () -> {
			startedAt.set(System.currentTimeMillis());
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
		};
		String body = serializedCall("formulas", traceId, markStart, () -> webClient.post().uri("/v1/formulas")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve().bodyToMono(String.class).block(blockTimeout()));
		long latency = System.currentTimeMillis() - startedAt.get();

		FormulaGenerationResponse parsed = parse(body, FormulaGenerationResponse.class);
		verifyFormulaShape(parsed, traceId);
		return new PerfumeryAiResult<>(body, parsed, latency);
	}

	/**
	 * 바디로션 전용 설계 요청({@code /v1/applications/body-lotion/design}). 표준 향수 계약과
	 * 완전히 다른 응답 구조라 {@link #verifyFormulaShape}와 같은 엄격한 스키마 검증은 하지
	 * 않는다 - {@code status}만 있으면 최소한의 유효 응답으로 본다(1단계, 실제 성공 응답
	 * 예시를 아직 확인하지 못해 방어적으로 최소 검증만 함).
	 */
	public PerfumeryAiResult<LotionDesignResponse> designLotion(
			LotionEstimateRequest request, String traceId, Runnable onSlotAcquired) {
		AtomicLong startedAt = new AtomicLong();
		Runnable markStart = () -> {
			startedAt.set(System.currentTimeMillis());
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
		};
		String body = serializedCall("body-lotion-design", traceId, markStart,
				() -> webClient.post().uri("/v1/applications/body-lotion/design")
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(request)
						.retrieve().bodyToMono(String.class).block(blockTimeout()));
		long latency = System.currentTimeMillis() - startedAt.get();

		LotionDesignResponse parsed = parse(body, LotionDesignResponse.class);
		if (parsed.status() == null) {
			throw new BusinessException(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
		}
		return new PerfumeryAiResult<>(body, parsed, latency);
	}

	/**
	 * 자연어 입력을 검토해 보완 질문(있으면) 또는 확정 검토 결과({@code review_id})를 받는다
	 * - v2 연동 플로우의 진입점(BE 연동 답변 1단계). 같은 물리 컨테이너를 v1과 공유하므로
	 * 동시성 게이트·레이트 리밋도 그대로 적용한다.
	 */
	public PerfumeryAiResult<PrepareBriefResponse> prepareBrief(
			PrepareBriefRequest request, String traceId, Runnable onSlotAcquired) {
		AtomicLong startedAt = new AtomicLong();
		Runnable markStart = () -> {
			startedAt.set(System.currentTimeMillis());
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
		};
		String body = serializedCall("briefs-prepare", traceId, markStart, () -> webClient.post().uri("/v2/briefs/prepare")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve().bodyToMono(String.class).block(blockTimeout()));
		long latency = System.currentTimeMillis() - startedAt.get();
		return new PerfumeryAiResult<>(body, parse(body, PrepareBriefResponse.class), latency);
	}

	/** 보완 질문에 대한 답변을 보내 검토 결과를 갱신한다({@code prepareBrief}의 후속 단계). */
	public PerfumeryAiResult<ClarifyBriefResponse> clarifyBrief(
			ClarifyBriefRequest request, String traceId, Runnable onSlotAcquired) {
		AtomicLong startedAt = new AtomicLong();
		Runnable markStart = () -> {
			startedAt.set(System.currentTimeMillis());
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
		};
		String body = serializedCall("briefs-clarify", traceId, markStart, () -> webClient.post().uri("/v2/briefs/clarify")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve().bodyToMono(String.class).block(blockTimeout()));
		long latency = System.currentTimeMillis() - startedAt.get();
		return new PerfumeryAiResult<>(body, parse(body, ClarifyBriefResponse.class), latency);
	}

	/**
	 * 공개 자료 연결·운영자 증거 묶음 등록 상태. {@code /v1/catalog}처럼 같은 컨테이너에서 실제
	 * 연산을 하는 호출이라 동시성 게이트·레이트 리밋을 그대로 적용한다({@code /health}와 다름).
	 */
	public PerfumeryAiResult<EvidenceStatusResponse> evidenceStatus(String traceId) {
		String body = serializedCall("evidence-status", traceId, null, () -> webClient.get().uri("/v2/evidence/status")
				.retrieve().bodyToMono(String.class).block(blockTimeout()));
		return new PerfumeryAiResult<>(body, parse(body, EvidenceStatusResponse.class), 0L);
	}

	/** 원료별 공개 자료 연결 범위. {@code limit} 최대 500(Modal 계약). */
	public PerfumeryAiResult<EvidenceCoverageResponse> evidenceCoverage(int offset, int limit, String traceId) {
		String body = serializedCall("evidence-coverage", traceId, null, () -> webClient.get()
				.uri(uriBuilder -> uriBuilder.path("/v2/evidence/coverage")
						.queryParam("offset", offset)
						.queryParam("limit", limit)
						.build())
				.retrieve().bodyToMono(String.class).block(blockTimeout()));
		return new PerfumeryAiResult<>(body, parse(body, EvidenceCoverageResponse.class), 0L);
	}

	/** 배합·제품·지역·비용·정책 조건의 규제·공급 근거를 평가한다 - 실제 후보 확정과는 별개다. */
	public PerfumeryAiResult<AssessEvidenceResponse> assessEvidence(
			AssessEvidenceRequest request, String traceId, Runnable onSlotAcquired) {
		AtomicLong startedAt = new AtomicLong();
		Runnable markStart = () -> {
			startedAt.set(System.currentTimeMillis());
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
		};
		String body = serializedCall("formulas-assess-evidence", traceId, markStart,
				() -> webClient.post().uri("/v2/formulas/assess-evidence")
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(request)
						.retrieve().bodyToMono(String.class).block(blockTimeout()));
		long latency = System.currentTimeMillis() - startedAt.get();
		return new PerfumeryAiResult<>(body, parse(body, AssessEvidenceResponse.class), latency);
	}

	/**
	 * 원료·공급 근거 버전이 바뀌었을 때 기존 배합이 여전히 유효한지 재평가한다
	 * ({@code previous_evidence_version} 대비). {@code assess-evidence}와 달리 진단 모드 우회가
	 * 없다 - 비교할 근거 자료가 전혀 없으면 항상 422로 거부된다(AI팀 확인, 2026-09-16:
	 * {@code {"detail":{"status":"abstained","code":"EVIDENCE_SNAPSHOTS_MISSING"}}}) - 이때는
	 * {@link ErrorCode#CHANGE_IMPACT_EVIDENCE_MISSING}으로 구분해서 던진다. 성공(200) 응답
	 * 스키마는 V89-backend-contract-rc01 실제 호출로 확인됐다({@link ChangeImpactResponse} 참고).
	 */
	public PerfumeryAiResult<ChangeImpactResponse> changeImpact(
			ChangeImpactRequest request, String traceId, Runnable onSlotAcquired) {
		AtomicLong startedAt = new AtomicLong();
		Runnable markStart = () -> {
			startedAt.set(System.currentTimeMillis());
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
		};
		String body = serializedCall("formulas-change-impact", traceId, markStart,
				() -> webClient.post().uri("/v2/formulas/change-impact")
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(request)
						.exchangeToMono(response -> response.statusCode().value() == 422
								? response.bodyToMono(String.class)
								: response.statusCode().isError()
										? response.createException().flatMap(Mono::error)
										: response.bodyToMono(String.class))
						.block(blockTimeout()));
		rejectChangeImpactError(traceId, body);
		long latency = System.currentTimeMillis() - startedAt.get();
		return new PerfumeryAiResult<>(body, parse(body, ChangeImpactResponse.class), latency);
	}

	/**
	 * change-impact의 422 응답은 {@code {"detail": {...}}}로 감싸여 온다 - 성공(200) 스키마에는
	 * 이 래퍼가 없으므로, {@code detail}이 있으면 무조건 오류로 본다(그대로 {@link #parse}에
	 * 넘기면 대부분 필드가 null인 "성공"으로 잘못 파싱된다). 비교할 근거 자료가 전혀 없는
	 * 경우({@code code: EVIDENCE_SNAPSHOTS_MISSING}, AI팀 확인 2026-09-16)만 구분해서 별도
	 * 오류로 던지고, 그 외(잘못된 버전 문자열 등 입력 오류)는 기존과 같이 일반 오류로 던진다.
	 */
	private void rejectChangeImpactError(String traceId, String body) {
		JsonNode detail;
		try {
			detail = jsonMapper.readTree(body).get("detail");
		} catch (JacksonException e) {
			return;
		}
		if (detail == null) {
			return;
		}
		if ("EVIDENCE_SNAPSHOTS_MISSING".equals(textOrNull(detail, "code"))) {
			log.info("[AI] op=formulas-change-impact trace={} abstained code=EVIDENCE_SNAPSHOTS_MISSING", traceId);
			throw new BusinessException(ErrorCode.CHANGE_IMPACT_EVIDENCE_MISSING);
		}
		log.error("[AI] op=formulas-change-impact trace={} rejected detail={}", traceId, detail);
		throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return value != null && value.isString() ? value.asString() : null;
	}

	/**
	 * 확인된 검토 결과({@code review_id})를 조향식 후보로 확정한다. 등록된 규제·공급 근거가
	 * 없으면 일반 호출은 422/abstained로 거부된다. {@code diagnostic_only=true}면 근거 미등록
	 * 상태에서도 HTTP 200을 반환하지만 {@code candidates}는 비고 결과는
	 * {@code diagnostic_candidates}에만 담긴다(AI 개발팀 확인, 2026-09-16) - 정식 승인 후보가
	 * 아니므로 호출부가 반드시 {@link EvaluationResponse#isDiagnostic()}으로 구분해야 한다.
	 */
	public PerfumeryAiResult<EvaluationResponse> evaluateFormula(
			EvaluateFormulaRequest request, String traceId, Runnable onSlotAcquired) {
		AtomicLong startedAt = new AtomicLong();
		Runnable markStart = () -> {
			startedAt.set(System.currentTimeMillis());
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
		};
		String body = serializedCall("formulas-evaluate", traceId, markStart,
				() -> webClient.post().uri("/v2/formulas/evaluate")
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(request)
						.retrieve().bodyToMono(String.class).block(blockTimeout()));
		long latency = System.currentTimeMillis() - startedAt.get();
		return new PerfumeryAiResult<>(body, parse(body, EvaluationResponse.class), latency);
	}

	/** 고정 배합을 유지한 채 조건만 재평가한다. {@link #evaluateFormula}와 같은 근거·응답 스키마 제약. */
	public PerfumeryAiResult<EvaluationResponse> reassessFormula(
			ReassessFormulaRequest request, String traceId, Runnable onSlotAcquired) {
		AtomicLong startedAt = new AtomicLong();
		Runnable markStart = () -> {
			startedAt.set(System.currentTimeMillis());
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
		};
		String body = serializedCall("formulas-reassess", traceId, markStart,
				() -> webClient.post().uri("/v2/formulas/reassess")
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(request)
						.retrieve().bodyToMono(String.class).block(blockTimeout()));
		long latency = System.currentTimeMillis() - startedAt.get();
		return new PerfumeryAiResult<>(body, parse(body, EvaluationResponse.class), latency);
	}

	/**
	 * BE가 보존한 후보 평가 스냅샷 2~10개를 비교한다. evaluate/reassess 성공과 달리 근거 등록을
	 * 직접 요구하지 않는다(README 확인) - {@code evaluation}에 진단(diagnostic) 결과를 넣어도 된다.
	 */
	public PerfumeryAiResult<CompareCandidatesResponse> compareCandidates(
			CompareCandidatesRequest request, String traceId, Runnable onSlotAcquired) {
		AtomicLong startedAt = new AtomicLong();
		Runnable markStart = () -> {
			startedAt.set(System.currentTimeMillis());
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
		};
		String body = serializedCall("formulas-compare", traceId, markStart,
				() -> webClient.post().uri("/v2/formulas/compare")
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(request)
						.retrieve().bodyToMono(String.class).block(blockTimeout()));
		long latency = System.currentTimeMillis() - startedAt.get();
		return new PerfumeryAiResult<>(body, parse(body, CompareCandidatesResponse.class), latency);
	}

	/**
	 * 저장 후보와 자연어 지시로 수정된 입력/검토 결과를 만든다. 새 후보를 저장·승인하지
	 * 않는다 - {@code next_operation}이 안내하는 재확인 절차를 호출부가 따라야 한다.
	 */
	public PerfumeryAiResult<ReviseCandidateResponse> reviseCandidate(
			ReviseCandidateRequest request, String traceId, Runnable onSlotAcquired) {
		AtomicLong startedAt = new AtomicLong();
		Runnable markStart = () -> {
			startedAt.set(System.currentTimeMillis());
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
		};
		String body = serializedCall("briefs-revise", traceId, markStart,
				() -> webClient.post().uri("/v2/briefs/revise")
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(request)
						.retrieve().bodyToMono(String.class).block(blockTimeout()));
		long latency = System.currentTimeMillis() - startedAt.get();
		return new PerfumeryAiResult<>(body, parse(body, ReviseCandidateResponse.class), latency);
	}

	// --- 호출 파이프라인: 인증 확인 → 동시성 게이트 → 레이트 리밋 → 재시도 ---

	/**
	 * @param onSlotAcquired 동시성 게이트를 획득한 직후, 큐 대기·재시도 시간을 뺀 시각을 표시하려는
	 *     호출자에게 신호를 주는 훅. 없으면 {@code null}.
	 */
	private String serializedCall(String op, String traceId, Runnable onSlotAcquired, Supplier<String> call) {
		requireAuthToken(op, traceId);
		acquireGate(op, traceId);
		try {
			if (onSlotAcquired != null) {
				onSlotAcquired.run();
			}
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
				String result = execute(op, traceId, call);
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
				// BE-108: 재시도 여부는 e.retryable(AI가 본문에 명시했으면 그 값, 아니면 HTTP 상태로
				// 추정)을 그대로 물려준다 - JobExecutor가 오류 코드만 보고 다시 재시도 가능으로
				// 되돌리면(예: 항상 재시도 가능하다고 오해하기 쉬운 AI_SERVICE_ERROR), 같은
				// 입력값으로 영원히 실패할 요청을 계속 재시도하게 된다(AI/프론트 개발팀 확인,
				// 2026-09-17). AI가 준 사유가 있으면 메시지로 함께 실어, 실패 화면에서 원인을 볼 수 있다.
				if (e.reason != null) {
					throw new BusinessException(e.errorCode, e.reason, e.retryable);
				}
				throw new BusinessException(e.errorCode, e.retryable);
			}
		}
	}

	/**
	 * WebClient 호출을 실행하고 발생 예외를 {@link AiCallException}(재시도 여부 포함)으로 변환한다.
	 * 4xx(재시도 불가로 분류되는 경우 포함)는 원문 응답 본문까지 로그에 남긴다 - 이전에는 오류
	 * 코드만 남아 조향 AI가 무엇을 거절했는지(예: 입력 검증 실패) 알 수 없었다(AI 개발팀 확인).
	 */
	private String execute(String op, String traceId, Supplier<String> call) {
		try {
			String body = call.get();
			// 오류 상태가 아니면서 본문이 비어 있는 응답(예: 따라가지 못한 3xx, 204)은 WebClient가 예외 없이
			// null을 돌려준다 - 그대로 파싱하면 'argument "content" is null' 같은 정체불명의 예외가 되어
			// 실패 사유를 알 수 없다(2026-09-19). 분명한 사유로 바꾸고 일시 오류처럼 한 번 재시도한다.
			if (body == null || body.isBlank()) {
				log.error("[AI] op={} trace={} empty response body (no error status, nothing to parse)", op, traceId);
				throw new AiCallException(ErrorCode.AI_SERVICE_ERROR, true,
						"조향 AI가 빈 응답을 돌려줬습니다. 잠시 후 다시 시도해 주세요.");
			}
			return body;
		} catch (WebClientResponseException e) {
			int status = e.getStatusCode().value();
			if (status == 401 || status == 403) {
				log.error("[AI] op={} trace={} status={} body={}", op, traceId, status, e.getResponseBodyAsString());
				throw new AiCallException(ErrorCode.AI_AUTH_MISCONFIGURED, false);
			}
			if (status == 429) {
				throw new AiCallException(ErrorCode.AI_RATE_LIMIT_EXCEEDED, true);
			}
			AiErrorDetail detail = AiErrorDetail.parse(jsonMapper, e.getResponseBodyAsString());
			if (e.getStatusCode().is5xxServerError()) {
				log.error("[AI] op={} trace={} status={} body={}", op, traceId, status, e.getResponseBodyAsString());
				// AI가 본문에 retryable을 명시했으면 HTTP 상태 추정보다 우선한다 - 같은 입력이면 항상
				// 실패하는 요청(예: LANGUAGE_DUPLICATE_CONSTRAINT, retryable=false)을 한 번 더 보내
				// 사용자가 같은 대기 시간을 두 번 겪지 않게 한다.
				throw new AiCallException(ErrorCode.AI_SERVICE_ERROR,
						detail.retryable() != null ? detail.retryable() : true, detail.reason());
			}
			log.error("[AI] op={} trace={} status={} body={} (non-5xx, non-retryable)",
					op, traceId, status, e.getResponseBodyAsString());
			throw new AiCallException(ErrorCode.AI_SERVICE_ERROR,
					detail.retryable() != null ? detail.retryable() : false, detail.reason());
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
		/** AI가 응답 본문에 실어 준 사유. 없으면 {@code null}이고 오류 코드의 기본 메시지를 쓴다. */
		private final String reason;

		private AiCallException(ErrorCode errorCode, boolean retryable) {
			this(errorCode, retryable, null);
		}

		private AiCallException(ErrorCode errorCode, boolean retryable, String reason) {
			this.errorCode = errorCode;
			this.retryable = retryable;
			this.reason = reason;
		}
	}

	/**
	 * AI 오류 응답 본문 {@code {"detail": {"code", "message", "retryable", ...}}}에서 재시도 여부와
	 * 사용자에게 보일 사유를 뽑는다. 이 모양이 아닌 본문(예: 422의 {@code detail}이 문자열·배열)은
	 * 둘 다 {@code null}이라 기존 판단(HTTP 상태)을 그대로 따른다. 사유는 {@code message (code)}
	 * 형태이며, 저장·화면 노출을 고려해 길이를 제한한다.
	 */
	private record AiErrorDetail(Boolean retryable, String reason) {

		private static final int REASON_MAX_LENGTH = 300;
		private static final AiErrorDetail NONE = new AiErrorDetail(null, null);

		static AiErrorDetail parse(JsonMapper jsonMapper, String body) {
			if (body == null || body.isBlank()) {
				return NONE;
			}
			JsonNode detail;
			try {
				detail = jsonMapper.readTree(body).get("detail");
			} catch (JacksonException e) {
				return NONE;
			}
			if (detail == null || !detail.isObject()) {
				return NONE;
			}
			JsonNode retryableNode = detail.get("retryable");
			Boolean retryable = retryableNode != null && retryableNode.isBoolean() ? retryableNode.asBoolean() : null;
			String message = textOrNull(detail, "message");
			String code = textOrNull(detail, "code");
			String reason = message != null
					? (code != null ? message + " (" + code + ")" : message)
					: code;
			if (reason != null && reason.length() > REASON_MAX_LENGTH) {
				reason = reason.substring(0, REASON_MAX_LENGTH);
			}
			return new AiErrorDetail(retryable, reason);
		}
	}
}
