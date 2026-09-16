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
	void succeeded_on_a_non_running_job_is_silently_ignored() {
		Job job = newJob();
		job.markSucceeded(job.getAttempt(), 1L);

		assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
		assertThat(job.getResultRefId()).isNull();
	}

	/** BE-041: 취소된 작업에 늦게 도착한 성공/실패가 덮어쓰기·예외 루프 없이 조용히 무시돼야 한다. */
	@Test
	void a_cancelled_job_ignores_late_success_and_failure_without_throwing() {
		Job job = newJob();
		int attempt = job.markRunning();
		job.cancel();

		job.markSucceeded(attempt, 999L);
		assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
		assertThat(job.getResultRefId()).isNull();

		job.markFailed(attempt, "late failure", true);
		assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
		assertThat(job.getFailureReason()).isNull();
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

	/** BE-044: 재시작 시점에 RUNNING이던 작업은 attempt와 무관하게 강제로 재시도 가능한 FAILED로 전환된다. */
	@Test
	void orphaned_running_job_is_marked_failed_and_retryable_regardless_of_attempt() {
		Job job = newJob();
		job.markRunning();

		job.markOrphaned("프로세스 재시작으로 실행이 중단되어 재시도 대상으로 전환됨");

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.isRetryable()).isTrue();
		assertThat(job.getFailureReason()).contains("재시작");
	}

	/** BE-044: PENDING/이미 종료된 작업은 markOrphaned가 조용히 무시한다. */
	@Test
	void markOrphaned_is_a_no_op_for_a_job_that_is_not_running() {
		Job pending = newJob();

		pending.markOrphaned("무시되어야 함");

		assertThat(pending.getStatus()).isEqualTo(JobStatus.PENDING);
		assertThat(pending.isRetryable()).isFalse();
	}
}
