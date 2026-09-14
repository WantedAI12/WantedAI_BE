package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobExecutor;
import com.perfumeryaicore.domain.job.service.JobService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * 후보 조향식 생성 오케스트레이션: 확정된 요청 검증 → Job 등록 → 비동기로 Modal 호출 → 결과 저장.
 *
 * <p>안전 제약을 만족하는 해가 없으면({@code no_safe_match}) 후보를 저장하지 않고
 * 작업을 {@code GENERATION_REJECTED} 사유로 실패 처리한다(재시도 대상 아님).
 */
@Slf4j
@Service
public class CandidateGenerationService {

	/** SUPPLIER·AUDITOR는 후보 생성을 트리거할 수 없다(BE-004). */
	private static final ProjectRole[] TRIGGER_ROLES = {
			ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND
	};

	private final FragranceRequestService fragranceRequestService;
	private final JobService jobService;
	private final JobExecutor jobExecutor;
	private final PerfumeryAiClient perfumeryAiClient;
	private final FormulaRequestMapper formulaRequestMapper;
	private final CandidatePersistenceService candidatePersistenceService;
	private final ProjectAccessGuard accessGuard;

	/**
	 * {@code jobService}는 {@code @Lazy}로 받는다: {@code JobService}가 {@code JobRetryHandler}
	 * 목록을 세터로 주입받는데, 그 구현체({@link CandidateGenerationRetryHandler})가 이 서비스를
	 * 다시 필요로 하는 순환 구조라서다. 여기서 즉시 해석을 요구하면 이 빈의 생성자가 끝나지 않아
	 * 조기 참조를 노출할 수 없다 — 지연 프록시로 생성자를 먼저 완료시켜야 순환이 풀린다.
	 */
	public CandidateGenerationService(
			FragranceRequestService fragranceRequestService,
			@Lazy JobService jobService,
			JobExecutor jobExecutor,
			PerfumeryAiClient perfumeryAiClient,
			FormulaRequestMapper formulaRequestMapper,
			CandidatePersistenceService candidatePersistenceService,
			ProjectAccessGuard accessGuard) {
		this.fragranceRequestService = fragranceRequestService;
		this.jobService = jobService;
		this.jobExecutor = jobExecutor;
		this.perfumeryAiClient = perfumeryAiClient;
		this.formulaRequestMapper = formulaRequestMapper;
		this.candidatePersistenceService = candidatePersistenceService;
		this.accessGuard = accessGuard;
	}

	/** 트리거 API에서 호출. 확정되지 않은 요청이면 작업을 만들지 않고 즉시 409. */
	public JobResponse enqueue(Long requestId, Long memberId) {
		return enqueue(requestId, memberId, null);
	}

	/**
	 * @param idempotencyKey 있으면 중복 제출 방지(BE-046)에 쓴다 — 같은 회원·같은 요청으로 이미
	 *                       만든 작업이 있으면 새로 만들지 않고 그 작업을 그대로 반환한다.
	 */
	public JobResponse enqueue(Long requestId, Long memberId, String idempotencyKey) {
		FragranceRequest request = fragranceRequestService.getConfirmedRequest(requestId, memberId);
		accessGuard.requireRole(request.getProjectId(), memberId, TRIGGER_ROLES);

		Job job = jobService.enqueue(request.getProjectId(), JobType.CANDIDATE_GENERATION, memberId,
				String.valueOf(requestId), idempotencyKey);
		dispatch(job.getId(), requestId, memberId);
		return jobService.get(job.getId(), memberId);
	}

	/** 최초 실행과 재시도({@link CandidateGenerationRetryHandler})가 공유하는 실행 경로. */
	public void dispatch(Long jobId, Long requestId, Long memberId) {
		jobExecutor.execute(jobId, JobType.CANDIDATE_GENERATION,
				context -> generate(jobId, requestId, memberId, context));
	}

	private Long generate(Long jobId, Long requestId, Long memberId, JobExecutor.JobContext context) {
		// 재시도 시점 기준으로 다시 확인 (그 사이 요청이 수정·차단됐을 수 있음)
		FragranceRequest request = fragranceRequestService.getConfirmedRequest(requestId, memberId);
		FormulaGenerationRequest modalRequest = formulaRequestMapper.toModalRequest(request);

		// BE-048: 동시성 게이트를 실제로 통과한 직후에만 시작을 기록한다 - 이전에는 세마포어
		// 대기열에서 기다린 시간까지 'AI 호출 경과 시간'에 섞여 들어갔다.
		PerfumeryAiResult result = perfumeryAiClient.generateFormula(modalRequest, "job-" + jobId, context::aiCallStarted);

		if (context.isCancelled()) {
			log.info("[FORMULA] job={} request={} cancelled before persisting result", jobId, requestId);
			throw new BusinessException(ErrorCode.JOB_CANCELLED);
		}

		if (result.parsed().isNoSafeMatch()) {
			String reason = result.parsed().message();
			log.info("[FORMULA] job={} request={} no_safe_match: {}", jobId, requestId, reason);
			candidatePersistenceService.persistRejection(requestId, request.getProjectId(), jobId, memberId, result);
			throw new BusinessException(ErrorCode.GENERATION_REJECTED,
					reason != null ? reason : ErrorCode.GENERATION_REJECTED.getMessage());
		}

		return candidatePersistenceService.persist(requestId, request.getProjectId(), memberId, jobId, result);
	}
}
