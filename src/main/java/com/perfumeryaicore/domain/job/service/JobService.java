package com.perfumeryaicore.domain.job.service;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 비동기 작업 생성·조회·재시도·취소.
 *
 * <p>작업 <em>본문</em> 실행은 {@link JobExecutor}가, 종류별 재시도 재구성은 도메인이 등록한
 * {@link JobRetryHandler}가 맡는다. 이 서비스는 수명주기와 접근 제어만 책임진다.
 * 접근 제어는 작업이 속한 프로젝트의 멤버십 기준({@link ProjectAccessGuard}).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class JobService {

	private final JobRepository jobRepository;
	private final ProjectAccessGuard accessGuard;
	private final JobEventBroadcaster eventBroadcaster;
	private final Map<JobType, JobRetryHandler> retryHandlers = new EnumMap<>(JobType.class);

	public JobService(JobRepository jobRepository, ProjectAccessGuard accessGuard,
			JobEventBroadcaster eventBroadcaster) {
		this.jobRepository = jobRepository;
		this.accessGuard = accessGuard;
		this.eventBroadcaster = eventBroadcaster;
	}

	/**
	 * 작업 진행 상황 실시간 구독(AI 개발팀 제안, 2026-09-17) - 접근 제어는 일반 조회와 같다.
	 * 구독 시점의 현재 상태를 즉시 한 번 받고, 이후 상태 변화마다 갱신을 받는다.
	 */
	public SseEmitter subscribe(Long jobId, Long memberId) {
		return eventBroadcaster.subscribe(get(jobId, memberId));
	}

	/**
	 * {@link JobRetryHandler} 구현체는 보통 자신의 도메인 서비스를 거쳐 {@link JobService}로
	 * 되돌아오는 순환 의존을 만든다({@code CandidateGenerationRetryHandler → CandidateGenerationService
	 * → JobExecutor/JobService}). 이 목록을 생성자 인자로 받으면 순환을 끊을 방법이 없으므로
	 * 세터 주입으로 받는다 — 인스턴스 생성 이후 채워지므로 다른 빈들이 그 사이에 이 서비스의
	 * 참조를 먼저 얻을 수 있다.
	 */
	@Autowired(required = false)
	public void setRetryHandlers(List<JobRetryHandler> handlers) {
		for (JobRetryHandler handler : handlers) {
			JobRetryHandler previous = retryHandlers.put(handler.supportedType(), handler);
			if (previous != null) {
				throw new IllegalStateException(
						"JobRetryHandler 중복 등록: " + handler.supportedType());
			}
		}
	}

	/**
	 * 새 작업을 대기 상태로 만든다. 도메인 서비스는 반환된 작업 ID로 {@link JobExecutor#execute}를 호출한다.
	 *
	 * @param inputPayload 재시도 시 요청 재구성을 위한 입력 JSON (없으면 {@code null})
	 */
	@Transactional
	public Job enqueue(Long projectId, JobType jobType, Long memberId, String inputPayload) {
		return enqueue(projectId, jobType, memberId, inputPayload, null);
	}

	/**
	 * {@code idempotencyKey}가 있으면 중복 제출을 막는다(BE-046). 같은 회원·작업 종류·키로 이미
	 * 만든 작업이 있으면: 입력이 같으면 새로 만들지 않고 그 작업을 그대로 반환하고(재시도/더블
	 * 클릭에 안전), 입력이 다르면 같은 키를 다른 요청에 재사용한 것이므로 409로 거부한다.
	 *
	 * <p>반환된 기존 작업을 호출자가 다시 {@link JobExecutor#execute}에 넘겨도 안전하다 — 이미
	 * PENDING이 아니면(RUNNING·SUCCEEDED 등) {@link #markRunning}이 그냥 -1을 돌려주고, 여전히
	 * PENDING이면 낙관적 잠금(BE-043)이 중복 실행을 막는다.
	 */
	@Transactional
	public Job enqueue(Long projectId, JobType jobType, Long memberId, String inputPayload, String idempotencyKey) {
		if (idempotencyKey != null) {
			Job existing = jobRepository
					.findByCreatedByAndJobTypeAndIdempotencyKey(memberId, jobType, idempotencyKey)
					.orElse(null);
			if (existing != null) {
				if (!Objects.equals(existing.getInputPayload(), inputPayload)) {
					throw new BusinessException(ErrorCode.JOB_IDEMPOTENCY_KEY_CONFLICT);
				}
				log.info("[JOB] idempotency key hit id={} type={} key={} - returning existing job",
						existing.getId(), jobType, idempotencyKey);
				return existing;
			}
		}
		Job job = jobRepository.save(Job.pending(projectId, jobType, memberId, inputPayload, idempotencyKey));
		log.info("[JOB] id={} type={} PENDING project={} by={}", job.getId(), jobType, projectId, memberId);
		return job;
	}

	public JobResponse get(Long jobId, Long memberId) {
		return JobResponse.from(getAccessibleJob(jobId, memberId));
	}

	// --- JobExecutor 전용 수명주기 (각각 독립 트랜잭션) ---

	/**
	 * @return 실제로 RUNNING으로 전이했으면 새로 시작된 시도의 attempt 번호(1부터).
	 *     작업이 없거나 이미 PENDING이 아니면 -1.
	 */
	@Transactional
	public int markRunning(Long jobId) {
		Job job = jobRepository.findById(jobId).orElse(null);
		if (job == null || job.getStatus() != JobStatus.PENDING) {
			return -1;
		}
		int attempt = job.markRunning();
		publishAfterCommit(job);
		return attempt;
	}

	/** {@code attempt}가 해당 작업의 현재 시도가 아니면(이전 시도의 지연 응답) 무시한다. */
	@Transactional
	public void markAiCallStarted(Long jobId, int attempt) {
		jobRepository.findById(jobId).ifPresent(job -> {
			if (!job.isCurrentAttempt(attempt)) {
				log.info("[JOB] id={} stale aiCallStarted ignored (attempt={}, current={})",
						jobId, attempt, job.getAttempt());
				return;
			}
			job.markAiCallStarted(attempt);
		});
	}

	/** {@code attempt}가 해당 작업의 현재 시도가 아니거나(지연 응답) 이미 RUNNING이 아니면(취소됨 등) 무시한다. */
	@Transactional
	public void markSucceeded(Long jobId, int attempt, Long resultRefId) {
		Job job = jobRepository.findById(jobId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
		if (!job.isCurrentAttempt(attempt)) {
			log.warn("[JOB] id={} stale success ignored (attempt={}, current={})",
					jobId, attempt, job.getAttempt());
			return;
		}
		if (job.getStatus() != JobStatus.RUNNING) {
			log.info("[JOB] id={} success ignored: status is {} (likely cancelled)", jobId, job.getStatus());
			return;
		}
		job.markSucceeded(attempt, resultRefId);
		publishAfterCommit(job);
	}

	/** {@code attempt}가 해당 작업의 현재 시도가 아니거나(지연 응답) 이미 RUNNING이 아니면(취소됨 등) 무시한다. */
	@Transactional
	public void markFailed(Long jobId, int attempt, String reason, boolean retryable) {
		Job job = jobRepository.findById(jobId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
		if (!job.isCurrentAttempt(attempt)) {
			log.warn("[JOB] id={} stale failure ignored (attempt={}, current={})",
					jobId, attempt, job.getAttempt());
			return;
		}
		if (job.getStatus() != JobStatus.RUNNING) {
			log.info("[JOB] id={} failure ignored: status is {} (likely cancelled)", jobId, job.getStatus());
			return;
		}
		job.markFailed(attempt, reason, retryable);
		publishAfterCommit(job);
	}

	/**
	 * 이 attempt가 아직 결과를 커밋해도 되는 유효한 실행인지(취소되지 않았고, 재시도로 대체되지도
	 * 않았는지). 도메인의 {@link JobExecutor.JobWork}는 AI 호출 등 외부 작업이 끝난 뒤, DB/스토리지에
	 * 결과를 쓰기 직전에 이 값을 확인해야 한다({@link JobExecutor.JobContext#isCancelled()} 경유).
	 */
	public boolean isRunningAttempt(Long jobId, int attempt) {
		return jobRepository.findById(jobId)
				.map(job -> job.isRunningAttempt(attempt))
				.orElse(false);
	}

	@Transactional
	public void retry(Long jobId, Long memberId) {
		Job job = getAccessibleJob(jobId, memberId);
		JobRetryHandler handler = retryHandlers.get(job.getJobType());
		if (handler == null) {
			throw new BusinessException(ErrorCode.JOB_RETRY_NOT_SUPPORTED);
		}
		job.resetForRetry();
		dispatchAfterCommit(() -> handler.redispatch(job));
		log.info("[JOB] id={} type={} retry dispatched", jobId, job.getJobType());
	}

	/**
	 * BE-042: 이 트랜잭션이 실제로 커밋된 뒤에만 {@code action}(비동기 워커 dispatch)을 실행한다.
	 * 커밋 전에 바로 dispatch하면, 비동기 워커가 아직 커밋되지 않은(예: 여전히 FAILED인) 상태를
	 * 읽고 실행을 건너뛰어 작업이 사실상 영구 PENDING으로 남을 수 있다. 활성 트랜잭션이 없으면
	 * (테스트 등) 즉시 실행한다.
	 */
	private void dispatchAfterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}

	/**
	 * 기동 직후 1회 실행되는 복구(BE-044). 단일 인스턴스 배포를 전제로 한다 — 기동 시점에 RUNNING인
	 * 작업은 이 프로세스가 방금 떴다는 뜻이므로 그 실행을 하던 (이전) 프로세스는 이미 죽었다고 본다.
	 * PENDING(아직 아무도 집어가지 않은) 작업과, 방금 고아로 판정해 FAILED(retryable)로 돌린 작업을
	 * 모두 등록된 {@link JobRetryHandler}로 다시 dispatch한다. 핸들러가 없는 종류는 로그만 남기고
	 * 건너뛴다 — 사람이 수동으로 확인해야 한다.
	 */
	@Transactional
	public void recoverAfterRestart() {
		List<Job> orphanedRunning = jobRepository.findByStatusOrderByCreatedAtAsc(JobStatus.RUNNING);
		for (Job job : orphanedRunning) {
			job.markOrphaned("프로세스 재시작으로 실행이 중단되어 재시도 대상으로 전환됨");
			log.warn("[JOB] id={} type={} orphaned RUNNING job marked FAILED(retryable) at startup",
					job.getId(), job.getJobType());
		}

		List<Job> pending = jobRepository.findByStatusOrderByCreatedAtAsc(JobStatus.PENDING);
		for (Job job : pending) {
			log.info("[JOB] id={} type={} recovering PENDING job left over from before restart",
					job.getId(), job.getJobType());
			redispatchIfPossible(job);
		}
		for (Job job : orphanedRunning) {
			job.resetForRetry();
			redispatchIfPossible(job);
		}
	}

	private void redispatchIfPossible(Job job) {
		JobRetryHandler handler = retryHandlers.get(job.getJobType());
		if (handler == null) {
			log.warn("[JOB] id={} type={} has no retry handler registered; left for manual recovery",
					job.getId(), job.getJobType());
			return;
		}
		dispatchAfterCommit(() -> handler.redispatch(job));
	}

	@Transactional
	public JobResponse cancel(Long jobId, Long memberId) {
		Job job = getAccessibleJob(jobId, memberId);
		job.cancel();
		log.info("[JOB] id={} type={} CANCELLED by={}", jobId, job.getJobType(), memberId);
		publishAfterCommit(job);
		return JobResponse.from(job);
	}

	/** 트랜잭션이 실제로 커밋된 뒤에만 구독자에게 알린다 - 롤백될 수도 있는 상태를 미리 보내지 않는다. */
	private void publishAfterCommit(Job job) {
		JobResponse response = JobResponse.from(job);
		dispatchAfterCommit(() -> eventBroadcaster.publish(response));
	}

	/** 작업이 속한 프로젝트의 멤버만 접근 허용. */
	private Job getAccessibleJob(Long jobId, Long memberId) {
		Job job = jobRepository.findById(jobId)
				.orElseThrow(() -> new BusinessException(ErrorCode.JOB_NOT_FOUND));
		if (!accessGuard.isMember(job.getProjectId(), memberId)) {
			throw new BusinessException(ErrorCode.JOB_ACCESS_DENIED);
		}
		return job;
	}
}
