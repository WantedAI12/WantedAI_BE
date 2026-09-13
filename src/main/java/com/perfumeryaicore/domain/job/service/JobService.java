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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final Map<JobType, JobRetryHandler> retryHandlers = new EnumMap<>(JobType.class);

	public JobService(JobRepository jobRepository, ProjectAccessGuard accessGuard) {
		this.jobRepository = jobRepository;
		this.accessGuard = accessGuard;
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
		Job job = jobRepository.save(Job.pending(projectId, jobType, memberId, inputPayload));
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
		return job.markRunning();
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
		handler.redispatch(job);
		log.info("[JOB] id={} type={} retry dispatched", jobId, job.getJobType());
	}

	@Transactional
	public JobResponse cancel(Long jobId, Long memberId) {
		Job job = getAccessibleJob(jobId, memberId);
		job.cancel();
		log.info("[JOB] id={} type={} CANCELLED by={}", jobId, job.getJobType(), memberId);
		return JobResponse.from(job);
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
