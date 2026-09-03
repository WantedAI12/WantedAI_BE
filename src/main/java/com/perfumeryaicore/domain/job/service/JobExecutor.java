package com.perfumeryaicore.domain.job.service;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비동기 작업 본문 실행기. 도메인 서비스는 {@code jobService.enqueue(...)} 로 작업을 만든 뒤
 * {@link #execute}에 실제 처리 로직({@link JobWork})을 넘긴다. 상태 전이와 실패 기록은 이 클래스가 맡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobExecutor {

	/** AI 클라이언트가 던지는 일시 오류. 이 경우에만 작업을 재시도 가능으로 표시한다. */
	private static final Set<ErrorCode> RETRYABLE_ERRORS = EnumSet.of(
			ErrorCode.AI_SERVICE_TIMEOUT,
			ErrorCode.AI_RATE_LIMIT_EXCEEDED,
			ErrorCode.AI_SERVICE_ERROR);

	private final JobRepository jobRepository;

	/**
	 * 작업 본문. 도메인이 구현한다.
	 */
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
	@Transactional
	public void execute(Long jobId, JobWork work) {
		Job job = jobRepository.findById(jobId).orElse(null);
		if (job == null) {
			log.error("[JOB] execute skipped: job {} not found", jobId);
			return;
		}
		if (job.getStatus() != JobStatus.PENDING) {
			log.info("[JOB] execute skipped: job {} is {} (not PENDING)", jobId, job.getStatus());
			return;
		}

		job.markRunning();
		log.info("[JOB] id={} type={} RUNNING", jobId, job.getJobType());

		try {
			Long resultRefId = work.run(job::markAiCallStarted);
			job.markSucceeded(resultRefId);
			log.info("[JOB] id={} type={} SUCCEEDED resultRefId={}", jobId, job.getJobType(), resultRefId);
		} catch (BusinessException e) {
			boolean retryable = RETRYABLE_ERRORS.contains(e.getErrorCode());
			job.markFailed(e.getErrorCode().name() + ": " + e.getMessage(), retryable);
			log.warn("[JOB] id={} type={} FAILED code={} retryable={}",
					jobId, job.getJobType(), e.getErrorCode().name(), retryable);
		} catch (RuntimeException e) {
			job.markFailed("UNEXPECTED: " + e.getClass().getSimpleName(), false);
			log.error("[JOB] id={} type={} FAILED unexpectedly", jobId, job.getJobType(), e);
		}
	}
}
