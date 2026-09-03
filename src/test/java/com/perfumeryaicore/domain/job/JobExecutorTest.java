package com.perfumeryaicore.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.domain.job.service.JobExecutor;
import com.perfumeryaicore.domain.job.service.JobExecutor.JobWork;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobExecutorTest {

	private JobRepository jobRepository;
	private JobExecutor jobExecutor;

	@BeforeEach
	void setUp() {
		jobRepository = mock(JobRepository.class);
		jobExecutor = new JobExecutor(jobRepository);
	}

	private Job pendingJob() {
		Job job = Job.pending(10L, JobType.CANDIDATE_GENERATION, 1L, null);
		when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
		return job;
	}

	@Test
	void successful_work_marks_job_succeeded_with_result_and_ai_start_time() {
		Job job = pendingJob();

		jobExecutor.execute(1L, ctx -> {
			ctx.aiCallStarted();
			return 999L;
		});

		assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(job.getResultRefId()).isEqualTo(999L);
		assertThat(job.getAiCallStartedAt()).isNotNull();
	}

	@Test
	void transient_ai_error_marks_job_failed_and_retryable() {
		Job job = pendingJob();

		jobExecutor.execute(1L, ctx -> {
			throw new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT);
		});

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.isRetryable()).isTrue();
		assertThat(job.getFailureReason()).contains("AI_SERVICE_TIMEOUT");
	}

	@Test
	void permanent_ai_error_marks_job_failed_not_retryable() {
		Job job = pendingJob();

		jobExecutor.execute(1L, ctx -> {
			throw new BusinessException(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
		});

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.isRetryable()).isFalse();
	}

	@Test
	void unexpected_exception_marks_job_failed_not_retryable() {
		Job job = pendingJob();

		jobExecutor.execute(1L, ctx -> {
			throw new IllegalArgumentException("boom");
		});

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.isRetryable()).isFalse();
		assertThat(job.getFailureReason()).contains("UNEXPECTED");
	}

	@Test
	void cancelled_job_is_skipped_and_work_never_runs() {
		Job job = pendingJob();
		job.cancel();
		JobWork work = mock(JobWork.class);

		jobExecutor.execute(1L, work);

		assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
		verify(work, never()).run(any());
	}

	@Test
	void missing_job_is_ignored() {
		when(jobRepository.findById(7L)).thenReturn(Optional.empty());
		JobWork work = mock(JobWork.class);

		jobExecutor.execute(7L, work);

		verify(work, never()).run(any());
	}
}
