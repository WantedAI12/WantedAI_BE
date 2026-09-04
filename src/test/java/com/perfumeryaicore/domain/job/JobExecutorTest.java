package com.perfumeryaicore.domain.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
		when(jobService.markRunning(1L)).thenReturn(true);
	}

	@Test
	void successful_work_marks_job_succeeded_with_result_and_notifies_ai_start() {
		jobExecutor.execute(1L, JobType.CANDIDATE_GENERATION, ctx -> {
			ctx.aiCallStarted();
			return 999L;
		});

		verify(jobService).markAiCallStarted(1L);
		verify(jobService).markSucceeded(1L, 999L);
		verify(jobService, never()).markFailed(anyLong(), anyString(), anyBoolean());
	}

	@Test
	void transient_ai_error_marks_job_failed_and_retryable() {
		jobExecutor.execute(1L, JobType.CANDIDATE_GENERATION, ctx -> {
			throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT);
		});

		verify(jobService).markFailed(eq(1L), contains("AI_SERVICE_TIMEOUT"), eq(true));
		verify(jobService, never()).markSucceeded(anyLong(), any());
	}

	@Test
	void permanent_ai_error_marks_job_failed_not_retryable() {
		jobExecutor.execute(1L, JobType.CANDIDATE_GENERATION, ctx -> {
			throw new BusinessException(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
		});

		verify(jobService).markFailed(eq(1L), contains("AI_SCHEMA_VERSION_MISMATCH"), eq(false));
	}

	@Test
	void unexpected_exception_marks_job_failed_not_retryable() {
		jobExecutor.execute(1L, JobType.CANDIDATE_GENERATION, ctx -> {
			throw new IllegalArgumentException("boom");
		});

		verify(jobService).markFailed(eq(1L), contains("UNEXPECTED"), eq(false));
	}

	@Test
	void not_runnable_job_is_skipped_and_work_never_runs() {
		when(jobService.markRunning(2L)).thenReturn(false);
		JobWork work = mock(JobWork.class);

		jobExecutor.execute(2L, JobType.CANDIDATE_GENERATION, work);

		verify(work, never()).run(any());
		verify(jobService, never()).markSucceeded(anyLong(), any());
		verify(jobService, never()).markFailed(anyLong(), anyString(), anyBoolean());
	}
}
