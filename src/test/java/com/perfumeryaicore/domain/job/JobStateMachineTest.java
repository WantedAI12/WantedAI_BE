package com.perfumeryaicore.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class JobStateMachineTest {

	private Job newJob() {
		return Job.pending(10L, JobType.CANDIDATE_GENERATION, 1L, "{\"requestId\":5}");
	}

	@Test
	void running_then_succeeded_carries_result_ref() {
		Job job = newJob();
		int attempt = job.markRunning();
		job.markAiCallStarted(attempt);
		job.markSucceeded(attempt, 42L);

		assertThat(job.getStatus()).isEqualTo(JobStatus.SUCCEEDED);
		assertThat(job.getResultRefId()).isEqualTo(42L);
		assertThat(job.getAiCallQueuedAt()).isNotNull();
		assertThat(job.getAiCallStartedAt()).isNotNull();
		assertThat(job.isRetryable()).isFalse();
	}

	@Test
	void failed_retryable_can_be_reset_and_run_again() {
		Job job = newJob();
		int firstAttempt = job.markRunning();
		job.markFailed(firstAttempt, "AI_SERVICE_TIMEOUT: timeout", true);

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.isRetryable()).isTrue();

		job.resetForRetry();
		assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
		assertThat(job.getFailureReason()).isNull();
		assertThat(job.isRetryable()).isFalse();
		assertThat(job.getAiCallQueuedAt()).isNull();

		int secondAttempt = job.markRunning();
		assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
		assertThat(secondAttempt).isEqualTo(firstAttempt + 1);
	}

	@Test
	void failed_not_retryable_cannot_be_reset() {
		Job job = newJob();
		int attempt = job.markRunning();
		job.markFailed(attempt, "AI_SCHEMA_VERSION_MISMATCH", false);

		assertThatThrownBy(job::resetForRetry)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.JOB_NOT_RETRYABLE);
	}

	@Test
	void pending_job_can_be_cancelled_and_then_not_run() {
		Job job = newJob();
		job.cancel();

		assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
		assertThatThrownBy(job::markRunning)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.JOB_ILLEGAL_STATE);
	}

	@Test
	void terminal_job_cannot_be_cancelled() {
		Job job = newJob();
		int attempt = job.markRunning();
		job.markSucceeded(attempt, 1L);

		assertThatThrownBy(job::cancel)
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.JOB_NOT_CANCELLABLE);
	}

	@Test
	void succeeded_transition_requires_running_state() {
		Job job = newJob();
		assertThatThrownBy(() -> job.markSucceeded(job.getAttempt(), 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.JOB_ILLEGAL_STATE);
	}

	@Test
	void succeeded_with_a_stale_attempt_number_is_silently_ignored_instead_of_throwing() {
		Job job = newJob();
		int attempt = job.markRunning();
		job.markSucceeded(attempt - 1, 999L);

		assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
		assertThat(job.getResultRefId()).isNull();
	}

	@Test
	void ownership_check() {
		Job job = newJob();
		assertThat(job.isOwnedBy(1L)).isTrue();
		assertThat(job.isOwnedBy(2L)).isFalse();
	}
}
