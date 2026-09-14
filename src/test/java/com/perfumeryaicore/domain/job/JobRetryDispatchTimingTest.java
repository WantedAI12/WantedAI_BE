package com.perfumeryaicore.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.repository.JobRepository;
import com.perfumeryaicore.domain.job.service.JobRetryHandler;
import com.perfumeryaicore.domain.job.service.JobService;
import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.repository.ProjectRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * BE-042: {@code JobService.retry()}가 비동기 워커 dispatch를 트랜잭션 커밋 이후로 미루는지 검증한다.
 * 커밋 전에 바로 dispatch하면, 별도 스레드에서 즉시 실행되는 워커가 아직 커밋되지 않은(여전히
 * FAILED인) 상태를 읽고 실행을 건너뛰어 작업이 사실상 영구 PENDING으로 남을 수 있다.
 */
@SpringBootTest
class JobRetryDispatchTimingTest {

	@Autowired
	private JobService jobService;
	@Autowired
	private JobRepository jobRepository;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private ProjectMemberRepository projectMemberRepository;
	@Autowired
	private MemberRepository memberRepository;
	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	void redispatch_runs_only_after_the_retry_transaction_commits() {
		Project project = projectRepository.save(Project.create("재시도 타이밍 테스트", null, null, null));
		Member member = memberRepository.save(Member.builder()
				.email("retry-timing-" + System.nanoTime() + "@example.com")
				.passwordHash("x")
				.name("테스터")
				.build());
		projectMemberRepository.save(ProjectMember.create(project.getId(), member.getId(), ProjectRole.ORG_ADMIN));

		Job job = jobRepository.save(
				Job.pending(project.getId(), JobType.SUPPLY_IMPACT_ANALYSIS, member.getId(), "input"));
		job.markRunning();
		job.markFailed(1, "boom", true);
		jobRepository.save(job);

		AtomicBoolean redispatched = new AtomicBoolean(false);
		jobService.setRetryHandlers(List.of(new JobRetryHandler() {
			@Override
			public JobType supportedType() {
				return JobType.SUPPLY_IMPACT_ANALYSIS;
			}

			@Override
			public void redispatch(Job j) {
				redispatched.set(true);
			}
		}));

		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.execute(status -> {
			jobService.retry(job.getId(), member.getId());
			// 아직 이 트랜잭션이 커밋되지 않았다 — dispatch가 여기서 이미 실행됐다면 버그다.
			assertThat(redispatched.get()).isFalse();
			return null;
		});

		// 트랜잭션 커밋 이후에는 dispatch가 실행되어 있어야 한다.
		assertThat(redispatched.get()).isTrue();
	}
}
