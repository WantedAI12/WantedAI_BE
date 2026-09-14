package com.perfumeryaicore.domain.project;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.repository.ProjectRepository;
import com.perfumeryaicore.domain.project.service.ProjectService;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * BE-009: 마지막 ORG_ADMIN 보호가 실제 동시 요청 아래에서도 지켜지는지 검증한다.
 *
 * <p>단위 테스트(Mockito)는 잠금 조회가 호출되는지 배선만 확인할 뿐, 두 스레드가 실제로 같은 행을
 * 두고 경합할 때 DB 잠금이 요청을 직렬화하는지는 증명하지 못한다. 그래서 이 테스트는 스프링
 * 컨테이너와 실제 H2 트랜잭션을 그대로 사용하고(테스트 트랜잭션 롤백 기능을 쓰지 않음),
 * 관리자 두 명 중 한 명을 두 스레드가 동시에 강등/제거하도록 만들어 정확히 하나만 성공하고
 * 나머지 하나는 {@link ErrorCode#PROJECT_LAST_ADMIN}(409)로 거부되는지 확인한다.
 */
@SpringBootTest
class LastAdminConcurrencyTest {

	@Autowired
	private ProjectService projectService;
	@Autowired
	private ProjectRepository projectRepository;
	@Autowired
	private ProjectMemberRepository projectMemberRepository;
	@Autowired
	private MemberRepository memberRepository;

	private Member createMember() {
		String email = "concurrency-" + UUID.randomUUID() + "@example.com";
		return memberRepository.save(Member.builder().email(email).passwordHash("x").name("테스터").build());
	}

	@Test
	void only_one_of_two_concurrent_requests_may_demote_the_last_two_admins() throws Exception {
		Project project = projectRepository.save(Project.create("동시성 테스트 프로젝트", null, null, null));
		Member admin1 = createMember();
		Member admin2 = createMember();
		projectMemberRepository.save(ProjectMember.create(project.getId(), admin1.getId(), ProjectRole.ORG_ADMIN));
		projectMemberRepository.save(ProjectMember.create(project.getId(), admin2.getId(), ProjectRole.ORG_ADMIN));

		// 서로가 서로를 대상으로 동시에 "관리자 제거"를 요청한다 — 둘 다 시작 시점에는
		// "관리자가 2명이니 통과"라고 볼 수 있는 경쟁 조건을 만들기 위해 CountDownLatch로 동시 출발시킨다.
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		AtomicReference<Throwable> failure1 = new AtomicReference<>();
		AtomicReference<Throwable> failure2 = new AtomicReference<>();

		Runnable removeAdmin1 = () -> {
			ready.countDown();
			awaitUnchecked(go);
			try {
				projectService.removeMember(project.getId(), admin2.getId(), admin1.getId());
			} catch (Throwable t) {
				failure1.set(t);
			}
		};
		Runnable removeAdmin2 = () -> {
			ready.countDown();
			awaitUnchecked(go);
			try {
				projectService.removeMember(project.getId(), admin1.getId(), admin2.getId());
			} catch (Throwable t) {
				failure2.set(t);
			}
		};

		pool.submit(removeAdmin1);
		pool.submit(removeAdmin2);
		ready.await(5, TimeUnit.SECONDS);
		go.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		List<ProjectMember> remaining = projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(project.getId());
		long remainingAdmins = remaining.stream().filter(ProjectMember::isAdmin).count();

		// 핵심 불변식: 두 요청이 동시에 들어와도 관리자가 0명이 되어서는 안 된다.
		assertThat(remainingAdmins).isEqualTo(1);

		// 정확히 한 요청은 성공(예외 없음)하고, 다른 한 요청은 PROJECT_LAST_ADMIN(409)로 거부되어야 한다.
		// (List.of는 null 원소를 거부하므로 성공한 쪽의 null을 담기 위해 Arrays.asList를 쓴다.)
		List<Throwable> failures = java.util.Arrays.asList(failure1.get(), failure2.get());
		long successCount = failures.stream().filter(f -> f == null).count();
		long rejectedCount = failures.stream()
				.filter(f -> f instanceof BusinessException be && be.getErrorCode() == ErrorCode.PROJECT_LAST_ADMIN)
				.count();

		assertThat(successCount).isEqualTo(1);
		assertThat(rejectedCount).isEqualTo(1);
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
