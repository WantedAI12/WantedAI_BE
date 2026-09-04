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
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JobServiceTest {

	private final JobRepository jobRepository = mock(JobRepository.class);

	private JobService service(JobRetryHandler... handlers) {
		JobService service = new JobService(jobRepository);
		service.setRetryHandlers(List.of(handlers));
		return service;
	}

	private Job job(long owner, JobStatus status, boolean retryable) {
		Job job = Job.pending(10L, JobType.CANDIDATE_GENERATION, owner, "{\"requestId\":5}");
		if (status == JobStatus.RUNNING || status == JobStatus.FAILED) {
			job.markRunning();
		}
		if (status == JobStatus.FAILED) {
			job.markFailed("AI_SERVICE_TIMEOUT", retryable);
		}
		return job;
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
}
