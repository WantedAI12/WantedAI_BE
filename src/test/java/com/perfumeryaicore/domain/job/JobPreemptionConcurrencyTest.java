package com.perfumeryaicore.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.domain.job.service.JobService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * BE-043: 워커 두 개가 같은 PENDING 작업을 동시에 선점해도 본문이 한 번만 실행되는지 실제 스레드로
 * 검증한다. 목 기반 단위 테스트로는 {@code @Version} 낙관적 잠금이 실제로 동시 커밋을 직렬화하는지
 * 증명할 수 없어서(project 도메인의 {@code LastAdminConcurrencyTest}와 같은 이유) 실제 스프링
 * 컨테이너와 H2 트랜잭션을 그대로 사용하는 통합 테스트로 작성한다.
 */
@SpringBootTest
class JobPreemptionConcurrencyTest {

	@Autowired
	private JobService jobService;
	@Autowired
	private JobRepository jobRepository;

	@Test
	void only_one_of_two_concurrent_preemptions_may_start_the_same_pending_job() throws Exception {
		Job job = jobRepository.save(Job.pending(1L, JobType.CANDIDATE_GENERATION, 1L, null));
		Long jobId = job.getId();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger startedCount = new AtomicInteger();
		AtomicInteger rejectedCount = new AtomicInteger();

		Runnable preempt = () -> {
			ready.countDown();
			awaitUnchecked(go);
			try {
				int attempt = jobService.markRunning(jobId);
				if (attempt > 0) {
					startedCount.incrementAndGet();
				} else {
					rejectedCount.incrementAndGet();
				}
			} catch (ObjectOptimisticLockingFailureException e) {
				rejectedCount.incrementAndGet();
			}
		};

		pool.submit(preempt);
		pool.submit(preempt);
		ready.await(5, TimeUnit.SECONDS);
		go.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		// 핵심 불변식: 두 워커가 동시에 선점을 시도해도 정확히 하나만 실제로 시작한다 —
		// 나머지 하나는 사업 로직상 거부(-1)되거나 낙관적 잠금 충돌로 거부된다. 둘 다 시작해서는 안 된다.
		assertThat(startedCount.get()).isEqualTo(1);
		assertThat(rejectedCount.get()).isEqualTo(1);

		Job reloaded = jobRepository.findById(jobId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(JobStatus.RUNNING);
		assertThat(reloaded.getAttempt()).isEqualTo(1);
	}

	private static void awaitUnchecked(CountDownLatch latch) {
		try {
			latch.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
