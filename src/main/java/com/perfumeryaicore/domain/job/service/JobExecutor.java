package com.perfumeryaicore.domain.job.service;

import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.EnumSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 비동기 작업 본문 실행기. 도메인 서비스는 {@code jobService.enqueue(...)} 로 작업을 만든 뒤
 * {@link #execute}에 실제 처리 로직({@link JobWork})을 넘긴다.
 *
 * <p>상태 전이는 {@link JobService}의 개별 트랜잭션 메서드로 수행한다. 작업 본문이 자체 트랜잭션을
 * 롤백해도 상태 기록(FAILED 등)이 오염되지 않도록, 실행과 상태 갱신 트랜잭션을 분리한다.
 */
@Slf4j
@Component
public class JobExecutor {

	/** AI 클라이언트가 던지는 일시 오류. 이 경우에만 작업을 재시도 가능으로 표시한다. */
	private static final Set<ErrorCode> RETRYABLE_ERRORS = EnumSet.of(
			ErrorCode.AI_SERVICE_TIMEOUT,
			ErrorCode.AI_RATE_LIMIT_EXCEEDED,
			ErrorCode.AI_SERVICE_ERROR);

	private final JobService jobService;

	/** {@code @Lazy}: {@link JobService}가 {@link JobRetryHandler} 목록을 세터로 주입받는데,
	 * 그 구현체가 도메인 서비스를 거쳐 이 실행기로 되돌아오는 순환 구조라서다. */
	public JobExecutor(@Lazy JobService jobService) {
		this.jobService = jobService;
	}

	/** 작업 본문. 도메인이 구현한다. */
	@FunctionalInterface
	public interface JobWork {
		/**
		 * @param context 실제 AI 호출 직전에 {@link JobContext#aiCallStarted()}를 호출해 시각을 남긴다
		 * @return 성공 시 생성된 도메인 리소스 식별자 (없으면 {@code null})
		 */
		Long run(JobContext context);
	}

	public interface JobContext {
		void aiCallStarted();

		/**
		 * 이 시도가 취소되었거나(사용자 취소) 재시도로 대체되어 더 이상 유효하지 않으면 {@code true}.
		 * AI 호출처럼 취소해도 실제로 멈추지 않는 외부 작업이 끝난 뒤, 도메인 코드가 그 결과를
		 * DB/스토리지에 커밋하기 <em>직전</em>에 반드시 확인해야 한다 — 취소된 작업의 결과가
		 * 후보·보고서 등으로 조용히 등록되는 것을 막는다.
		 */
		boolean isCancelled();
	}

	@Async("jobTaskExecutor")
	public void execute(Long jobId, JobType jobType, JobWork work) {
		int attempt;
		try {
			attempt = jobService.markRunning(jobId);
		} catch (ObjectOptimisticLockingFailureException e) {
			// BE-043: 다른 워커가 이 시도를 먼저 선점해 커밋했다. 본문을 두 번 실행하지 않고 조용히 넘어간다.
			log.info("[JOB] id={} execute skipped: lost concurrent preemption", jobId);
			return;
		}
		if (attempt < 0) {
			log.info("[JOB] execute skipped: job {} not runnable", jobId);
			return;
		}
		log.info("[JOB] id={} type={} attempt={} RUNNING", jobId, jobType, attempt);

		JobContext context = new JobContext() {
			@Override
			public void aiCallStarted() {
				jobService.markAiCallStarted(jobId, attempt);
			}

			@Override
			public boolean isCancelled() {
				return !jobService.isRunningAttempt(jobId, attempt);
			}
		};

		try {
			Long resultRefId = work.run(context);
			jobService.markSucceeded(jobId, attempt, resultRefId);
			log.info("[JOB] id={} type={} attempt={} SUCCEEDED resultRefId={}", jobId, jobType, attempt, resultRefId);
		} catch (BusinessException e) {
			boolean retryable = RETRYABLE_ERRORS.contains(e.getErrorCode());
			jobService.markFailed(jobId, attempt, e.getErrorCode().name() + ": " + e.getMessage(), retryable);
			log.warn("[JOB] id={} type={} attempt={} FAILED code={} retryable={}",
					jobId, jobType, attempt, e.getErrorCode().name(), retryable);
		} catch (RuntimeException e) {
			jobService.markFailed(jobId, attempt, "UNEXPECTED: " + e.getClass().getSimpleName(), false);
			log.error("[JOB] id={} type={} attempt={} FAILED unexpectedly", jobId, jobType, attempt, e);
		}
	}
}
