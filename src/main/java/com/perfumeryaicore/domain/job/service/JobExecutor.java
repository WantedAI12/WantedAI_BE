package com.perfumeryaicore.domain.job.service;

import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.EnumSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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

	@FunctionalInterface
	public interface JobContext {
		void aiCallStarted();
	}

	@Async("jobTaskExecutor")
	public void execute(Long jobId, JobType jobType, JobWork work) {
		if (!jobService.markRunning(jobId)) {
			log.info("[JOB] execute skipped: job {} not runnable", jobId);
			return;
		}
		log.info("[JOB] id={} type={} RUNNING", jobId, jobType);

		try {
			Long resultRefId = work.run(() -> jobService.markAiCallStarted(jobId));
			jobService.markSucceeded(jobId, resultRefId);
			log.info("[JOB] id={} type={} SUCCEEDED resultRefId={}", jobId, jobType, resultRefId);
		} catch (BusinessException e) {
			boolean retryable = RETRYABLE_ERRORS.contains(e.getErrorCode());
			jobService.markFailed(jobId, e.getErrorCode().name() + ": " + e.getMessage(), retryable);
			log.warn("[JOB] id={} type={} FAILED code={} retryable={}",
					jobId, jobType, e.getErrorCode().name(), retryable);
		} catch (RuntimeException e) {
			jobService.markFailed(jobId, "UNEXPECTED: " + e.getClass().getSimpleName(), false);
			log.error("[JOB] id={} type={} FAILED unexpectedly", jobId, jobType, e);
		}
	}
}
