package com.perfumeryaicore.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.domain.job.service.JobRetryHandler;
import com.perfumeryaicore.domain.job.service.JobService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JobServiceTest {

	private final JobRepository jobRepository = mock(JobRepository.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);

	private JobService service(JobRetryHandler... handlers) {
		when(accessGuard.isMember(10L, 1L)).thenReturn(true); // job(...) 아래서 projectId=10L, owner=1L 고정
		JobService service = new JobService(jobRepository, accessGuard);
		service.setRetryHandlers(List.of(handlers));
		return service;
	}

	private Job job(long owner, JobStatus status, boolean retryable) {
		Job job = Job.pending(10L, JobType.CANDIDATE_GENERATION, owner, "{\"requestId\":5}");
		if (status == JobStatus.RUNNING || status == JobStatus.FAILED) {
			job.markRunning();
		}
		if (status == JobStatus.FAILED) {
			job.markFailed(job.getAttempt(), "AI_SERVICE_TIMEOUT", retryable);
		}
		return job;
	}

	/** BE-046: 같은 키·같은 입력으로 다시 enqueue하면 새로 만들지 않고 기존 작업을 그대로 반환한다. */
	@Test
	void enqueue_with_a_repeated_idempotency_key_and_the_same_input_returns_the_existing_job() {
		Job existing = job(1L, JobStatus.PENDING, false);
		when(jobRepository.findByCreatedByAndJobTypeAndIdempotencyKey(1L, JobType.CANDIDATE_GENERATION, "key-1"))
				.thenReturn(Optional.of(existing));

		Job result = service().enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "{\"requestId\":5}", "key-1");

		assertThat(result).isSameAs(existing);
		verify(jobRepository, never()).save(any());
	}

	/** BE-046: 같은 키인데 입력이 다르면 - 같은 키를 다른 요청에 재사용한 것이므로 409로 거부한다. */
	@Test
	void enqueue_with_a_repeated_idempotency_key_but_different_input_is_rejected() {
		Job existing = job(1L, JobStatus.PENDING, false); // inputPayload = {"requestId":5}
		when(jobRepository.findByCreatedByAndJobTypeAndIdempotencyKey(1L, JobType.CANDIDATE_GENERATION, "key-1"))
				.thenReturn(Optional.of(existing));

		assertThatThrownBy(() -> service().enqueue(
				10L, JobType.CANDIDATE_GENERATION, 1L, "{\"requestId\":6}", "key-1"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.JOB_IDEMPOTENCY_KEY_CONFLICT);
		verify(jobRepository, never()).save(any());
	}

	/** BE-046: 처음 보는 키면 평소대로 새 작업을 만든다. */
	@Test
	void enqueue_with_a_new_idempotency_key_creates_a_new_job() {
		when(jobRepository.findByCreatedByAndJobTypeAndIdempotencyKey(1L, JobType.CANDIDATE_GENERATION, "key-2"))
				.thenReturn(Optional.empty());
		when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

		Job created = service().enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "{}", "key-2");

		assertThat(created.getStatus()).isEqualTo(JobStatus.PENDING);
		assertThat(created.getIdempotencyKey()).isEqualTo("key-2");
	}

	@Test
	void enqueue_persists_pending_job() {
		when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

		Job created = service().enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "{}");

		assertThat(created.getStatus()).isEqualTo(JobStatus.PENDING);
		assertThat(created.getJobType()).isEqualTo(JobType.CANDIDATE_GENERATION);
		verify(jobRepository).save(any(Job.class));
	}

	@Test
	void get_unknown_job_throws_not_found() {
		when(jobRepository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().get(99L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.JOB_NOT_FOUND);
	}

	@Test
	void get_by_non_owner_is_denied() {
		when(jobRepository.findById(1L)).thenReturn(Optional.of(job(1L, JobStatus.PENDING, false)));

		assertThatThrownBy(() -> service().get(1L, 2L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.JOB_ACCESS_DENIED);
	}

	@Test
	void cancel_by_owner_transitions_to_cancelled() {
		when(jobRepository.findById(1L)).thenReturn(Optional.of(job(1L, JobStatus.PENDING, false)));

		var response = service().cancel(1L, 1L);

		assertThat(response.status()).isEqualTo(JobStatus.CANCELLED);
	}

	@Test
	void retry_without_registered_handler_is_not_supported_and_leaves_state_untouched() {
		Job failed = job(1L, JobStatus.FAILED, true);
		when(jobRepository.findById(1L)).thenReturn(Optional.of(failed));

		assertThatThrownBy(() -> service().retry(1L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.JOB_RETRY_NOT_SUPPORTED);
		assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
	}

	@Test
	void retry_with_handler_resets_job_and_redispatches() {
		Job failed = job(1L, JobStatus.FAILED, true);
		when(jobRepository.findById(1L)).thenReturn(Optional.of(failed));

		AtomicReference<Job> redispatched = new AtomicReference<>();
		JobRetryHandler handler = new JobRetryHandler() {
			@Override
			public JobType supportedType() {
				return JobType.CANDIDATE_GENERATION;
			}

			@Override
			public void redispatch(Job job) {
				redispatched.set(job);
			}
		};

		service(handler).retry(1L, 1L);

		assertThat(redispatched.get()).isSameAs(failed);
		assertThat(failed.getStatus()).isEqualTo(JobStatus.PENDING);
		assertThat(failed.isRetryable()).isFalse();
	}

	@Test
	void retry_non_retryable_job_is_rejected_before_dispatch() {
		Job failed = job(1L, JobStatus.FAILED, false);
		when(jobRepository.findById(1L)).thenReturn(Optional.of(failed));

		AtomicReference<Job> redispatched = new AtomicReference<>();
		JobRetryHandler handler = new JobRetryHandler() {
			@Override
			public JobType supportedType() {
				return JobType.CANDIDATE_GENERATION;
			}

			@Override
			public void redispatch(Job job) {
				redispatched.set(job);
			}
		};

		assertThatThrownBy(() -> service(handler).retry(1L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.JOB_NOT_RETRYABLE);
		assertThat(redispatched.get()).isNull();
	}

	@Test
	void duplicate_retry_handlers_for_same_type_fail_fast() {
		JobRetryHandler a = handlerFor(JobType.CATALOG_SYNC);
		JobRetryHandler b = handlerFor(JobType.CATALOG_SYNC);

		assertThatThrownBy(() -> service(a, b))
				.isInstanceOf(IllegalStateException.class);
	}

	private JobRetryHandler handlerFor(JobType type) {
		return new JobRetryHandler() {
			@Override
			public JobType supportedType() {
				return type;
			}

			@Override
			public void redispatch(Job job) {
			}
		};
	}

	@Test
	void markRunning_returns_a_new_attempt_number_each_time_it_restarts() {
		Job pending = job(1L, JobStatus.PENDING, false);
		when(jobRepository.findById(1L)).thenReturn(Optional.of(pending));

		int firstAttempt = service().markRunning(1L);
		assertThat(firstAttempt).isEqualTo(1);

		pending.markFailed(firstAttempt, "AI_SERVICE_TIMEOUT", true);
		pending.resetForRetry();
		int secondAttempt = service().markRunning(1L);
		assertThat(secondAttempt).isEqualTo(2);
	}

	@Test
	void markSucceeded_from_a_superseded_attempt_is_ignored() {
		Job running = job(1L, JobStatus.RUNNING, false);
		int currentAttempt = running.getAttempt();
		when(jobRepository.findById(1L)).thenReturn(Optional.of(running));

		service().markSucceeded(1L, currentAttempt - 1, 999L);

		assertThat(running.getStatus()).isEqualTo(JobStatus.RUNNING);
		assertThat(running.getResultRefId()).isNull();
	}

	@Test
	void markFailed_from_a_superseded_attempt_is_ignored() {
		Job running = job(1L, JobStatus.RUNNING, false);
		int currentAttempt = running.getAttempt();
		when(jobRepository.findById(1L)).thenReturn(Optional.of(running));

		service().markFailed(1L, currentAttempt - 1, "STALE_TIMEOUT", true);

		assertThat(running.getStatus()).isEqualTo(JobStatus.RUNNING);
		assertThat(running.getFailureReason()).isNull();
	}

	@Test
	void markAiCallStarted_from_a_superseded_attempt_is_ignored() {
		Job running = job(1L, JobStatus.RUNNING, false);
		int currentAttempt = running.getAttempt();
		when(jobRepository.findById(1L)).thenReturn(Optional.of(running));

		service().markAiCallStarted(1L, currentAttempt - 1);

		assertThat(running.getAiCallStartedAt()).isNull();
	}

	/** BE-041: 취소 후 늦게 도착한 성공이 예외 없이 조용히 무시돼야 한다. */
	@Test
	void markSucceeded_on_a_cancelled_job_is_ignored_without_throwing() {
		Job running = job(1L, JobStatus.RUNNING, false);
		int attempt = running.getAttempt();
		running.cancel();
		when(jobRepository.findById(1L)).thenReturn(Optional.of(running));

		service().markSucceeded(1L, attempt, 999L);

		assertThat(running.getStatus()).isEqualTo(JobStatus.CANCELLED);
		assertThat(running.getResultRefId()).isNull();
	}

	/** BE-041: 취소 후 늦게 도착한 실패도 예외 루프 없이 조용히 무시돼야 한다. */
	@Test
	void markFailed_on_a_cancelled_job_is_ignored_without_throwing() {
		Job running = job(1L, JobStatus.RUNNING, false);
		int attempt = running.getAttempt();
		running.cancel();
		when(jobRepository.findById(1L)).thenReturn(Optional.of(running));

		service().markFailed(1L, attempt, "late failure", true);

		assertThat(running.getStatus()).isEqualTo(JobStatus.CANCELLED);
		assertThat(running.getFailureReason()).isNull();
	}

	@Test
	void isRunningAttempt_is_false_once_the_job_is_cancelled() {
		Job running = job(1L, JobStatus.RUNNING, false);
		int attempt = running.getAttempt();
		running.cancel();
		when(jobRepository.findById(1L)).thenReturn(Optional.of(running));

		assertThat(service().isRunningAttempt(1L, attempt)).isFalse();
	}

	@Test
	void isRunningAttempt_is_true_while_running_with_the_current_attempt() {
		Job running = job(1L, JobStatus.RUNNING, false);
		int attempt = running.getAttempt();
		when(jobRepository.findById(1L)).thenReturn(Optional.of(running));

		assertThat(service().isRunningAttempt(1L, attempt)).isTrue();
	}

	/** BE-044: 기동 시 PENDING과 고아 RUNNING 작업을 모두 등록된 핸들러로 다시 dispatch한다. */
	@Test
	void recoverAfterRestart_redispatches_pending_and_orphaned_running_jobs() {
		Job pendingJob = job(1L, JobStatus.PENDING, false);
		Job runningJob = job(1L, JobStatus.RUNNING, false);
		when(jobRepository.findByStatusOrderByCreatedAtAsc(JobStatus.RUNNING)).thenReturn(List.of(runningJob));
		when(jobRepository.findByStatusOrderByCreatedAtAsc(JobStatus.PENDING)).thenReturn(List.of(pendingJob));

		List<Job> redispatched = new java.util.ArrayList<>();
		JobRetryHandler handler = new JobRetryHandler() {
			@Override
			public JobType supportedType() {
				return JobType.CANDIDATE_GENERATION;
			}

			@Override
			public void redispatch(Job job) {
				redispatched.add(job);
			}
		};

		service(handler).recoverAfterRestart();

		// 고아였던 RUNNING 작업은 FAILED(retryable)로 전환된 뒤 다시 PENDING으로 리셋된다.
		assertThat(runningJob.getStatus()).isEqualTo(JobStatus.PENDING);
		assertThat(redispatched).containsExactlyInAnyOrder(pendingJob, runningJob);
	}

	/** BE-044: 등록된 재시도 핸들러가 없는 작업 종류는 자동 복구 대상에서 빠지고 그대로 남는다. */
	@Test
	void recoverAfterRestart_leaves_jobs_without_a_registered_handler_untouched() {
		Job pendingJob = job(1L, JobStatus.PENDING, false);
		when(jobRepository.findByStatusOrderByCreatedAtAsc(JobStatus.RUNNING)).thenReturn(List.of());
		when(jobRepository.findByStatusOrderByCreatedAtAsc(JobStatus.PENDING)).thenReturn(List.of(pendingJob));

		service().recoverAfterRestart();

		assertThat(pendingJob.getStatus()).isEqualTo(JobStatus.PENDING);
	}
}
