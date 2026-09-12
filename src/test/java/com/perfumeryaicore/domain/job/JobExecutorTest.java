package com.perfumeryaicore.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobExecutor;
import com.perfumeryaicore.domain.job.service.JobExecutor.JobWork;
import com.perfumeryaicore.domain.job.service.JobService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobExecutorTest {

	private JobService jobService;
	private JobExecutor jobExecutor;

	@BeforeEach
	void setUp() {
		jobService = mock(JobService.class);
		jobExecutor = new JobExecutor(jobService);
		when(jobService.markRunning(1L)).thenReturn(1);
	}

	@Test
	void successful_work_marks_job_succeeded_with_result_and_notifies_ai_start() {
		jobExecutor.execute(1L, JobType.CANDIDATE_GENERATION, ctx -> {
			ctx.aiCallStarted();
			return 999L;
		});

		verify(jobService).markAiCallStarted(1L, 1);
		verify(jobService).markSucceeded(1L, 1, 999L);
		verify(jobService, never()).markFailed(anyLong(), anyInt(), anyString(), anyBoolean());
	}

	@Test
	void transient_ai_error_marks_job_failed_and_retryable() {
		jobExecutor.execute(1L, JobType.CANDIDATE_GENERATION, ctx -> {
			throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT);
		});

		verify(jobService).markFailed(eq(1L), eq(1), contains("AI_SERVICE_TIMEOUT"), eq(true));
		verify(jobService, never()).markSucceeded(anyLong(), anyInt(), any());
	}

	@Test
	void permanent_ai_error_marks_job_failed_not_retryable() {
		jobExecutor.execute(1L, JobType.CANDIDATE_GENERATION, ctx -> {
			throw new BusinessException(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
		});

		verify(jobService).markFailed(eq(1L), eq(1), contains("AI_SCHEMA_VERSION_MISMATCH"), eq(false));
	}

	@Test
	void unexpected_exception_marks_job_failed_not_retryable() {
		jobExecutor.execute(1L, JobType.CANDIDATE_GENERATION, ctx -> {
			throw new IllegalArgumentException("boom");
		});

		verify(jobService).markFailed(eq(1L), eq(1), contains("UNEXPECTED"), eq(false));
	}

	@Test
	void not_runnable_job_is_skipped_and_work_never_runs() {
		when(jobService.markRunning(2L)).thenReturn(-1);
		JobWork work = mock(JobWork.class);

		jobExecutor.execute(2L, JobType.CANDIDATE_GENERATION, work);

		verify(work, never()).run(any());
		verify(jobService, never()).markSucceeded(anyLong(), anyInt(), any());
		verify(jobService, never()).markFailed(anyLong(), anyInt(), anyString(), anyBoolean());
	}

	/** BE-041: 도메인 코드가 결과를 커밋하기 직전에 확인하는 {@code JobContext#isCancelled()}가
	 * 실제로 {@link JobService#isRunningAttempt}에 위임되는지 검증한다. */
	@Test
	void context_isCancelled_reflects_jobService_isRunningAttempt() {
		when(jobService.isRunningAttempt(1L, 1)).thenReturn(false);
		boolean[] cancelled = {true};

		jobExecutor.execute(1L, JobType.CANDIDATE_GENERATION, ctx -> {
			cancelled[0] = ctx.isCancelled();
			return null;
		});

		assertThat(cancelled[0]).isTrue();
	}

	@Test
	void context_isCancelled_is_false_while_the_attempt_is_still_running() {
		when(jobService.isRunningAttempt(1L, 1)).thenReturn(true);
		boolean[] cancelled = {true};

		jobExecutor.execute(1L, JobType.CANDIDATE_GENERATION, ctx -> {
			cancelled[0] = ctx.isCancelled();
			return null;
		});

		assertThat(cancelled[0]).isFalse();
	}
}
