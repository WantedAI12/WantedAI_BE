package com.perfumeryaicore.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.member.dto.request.LoginRequest;
import com.perfumeryaicore.domain.member.dto.request.SignupRequest;
import com.perfumeryaicore.domain.member.dto.response.TokenResponse;
import com.perfumeryaicore.domain.member.service.AuthService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.Arrays;
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
 * BE-011: 같은 refresh token으로 들어온 두 개의 동시 회전 요청 중 정확히 하나만 성공하고,
 * 나머지는 재사용 탐지(409)로 거부되는지 실제 스레드로 검증한다.
 *
 * <p>단위 테스트만으로는 잠금 조회가 실제로 두 요청을 직렬화하는지 증명할 수 없어서
 * (project 도메인의 {@code LastAdminConcurrencyTest}와 같은 이유) 실제 스프링 컨테이너와
 * H2 트랜잭션을 그대로 사용하는 통합 테스트로 작성한다.
 */
@SpringBootTest
class RefreshTokenRotationConcurrencyTest {

	@Autowired
	private AuthService authService;

	@Test
	void only_one_of_two_concurrent_refresh_requests_may_rotate_the_same_token() throws Exception {
		String email = "refresh-race-" + UUID.randomUUID() + "@example.com";
		authService.signup(new SignupRequest(email, "password123", "테스터"));
		TokenResponse initial = authService.login(new LoginRequest(email, "password123"));
		String sharedRefreshToken = initial.refreshToken();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		AtomicReference<TokenResponse> success1 = new AtomicReference<>();
		AtomicReference<TokenResponse> success2 = new AtomicReference<>();
		AtomicReference<Throwable> failure1 = new AtomicReference<>();
		AtomicReference<Throwable> failure2 = new AtomicReference<>();

		Runnable rotate1 = () -> {
			ready.countDown();
			awaitUnchecked(go);
			try {
				success1.set(authService.refresh(sharedRefreshToken));
			} catch (Throwable t) {
				failure1.set(t);
			}
		};
		Runnable rotate2 = () -> {
			ready.countDown();
			awaitUnchecked(go);
			try {
				success2.set(authService.refresh(sharedRefreshToken));
			} catch (Throwable t) {
				failure2.set(t);
			}
		};

		pool.submit(rotate1);
		pool.submit(rotate2);
		ready.await(5, TimeUnit.SECONDS);
		go.countDown();
		pool.shutdown();
		assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

		List<TokenResponse> successes = Arrays.asList(success1.get(), success2.get());
		List<Throwable> failures = Arrays.asList(failure1.get(), failure2.get());

		long successCount = successes.stream().filter(r -> r != null).count();
		long reuseDetectedCount = failures.stream()
				.filter(f -> f instanceof BusinessException be
						&& be.getErrorCode() == ErrorCode.REFRESH_TOKEN_REUSE_DETECTED)
				.count();

		// 정확히 하나만 회전에 성공하고, 나머지 하나는 재사용 탐지(409)로 거부되어야 한다 —
		// 둘 다 성공(하나의 토큰에서 두 개의 유효한 세션이 생기는 것)해서는 안 된다.
		assertThat(successCount).isEqualTo(1);
		assertThat(reuseDetectedCount).isEqualTo(1);
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
