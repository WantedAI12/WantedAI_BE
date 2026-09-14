package com.perfumeryaicore.domain.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.perfumeryaicore.domain.member.entity.Member;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * BE-087: 로그인 실패 누적에 따른 계정 잠금 상태 전이를 검증한다.
 */
class MemberLockoutTest {

	private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 0, 0);

	private static Member member() {
		return Member.builder().email("user@example.com").passwordHash("hash").name("사용자").build();
	}

	@Test
	void failed_login_below_the_threshold_only_increments_the_counter() {
		Member member = member();

		member.recordFailedLogin(NOW, 5, LOCKOUT_DURATION);
		member.recordFailedLogin(NOW, 5, LOCKOUT_DURATION);

		assertThat(member.getFailedLoginAttempts()).isEqualTo(2);
		assertThat(member.isLocked(NOW)).isFalse();
	}

	@Test
	void reaching_the_max_attempts_locks_the_account_for_the_configured_duration() {
		Member member = member();

		for (int i = 0; i < 5; i++) {
			member.recordFailedLogin(NOW, 5, LOCKOUT_DURATION);
		}

		assertThat(member.isLocked(NOW)).isTrue();
		assertThat(member.isLocked(NOW.plusMinutes(14))).isTrue();
		assertThat(member.isLocked(NOW.plusMinutes(15))).isFalse();
	}

	@Test
	void a_failed_attempt_while_already_locked_does_not_extend_the_lockout() {
		Member member = member();
		for (int i = 0; i < 5; i++) {
			member.recordFailedLogin(NOW, 5, LOCKOUT_DURATION);
		}
		LocalDateTime lockedUntilFirst = member.getLockedUntil();

		member.recordFailedLogin(NOW.plusMinutes(5), 5, LOCKOUT_DURATION);

		assertThat(member.getLockedUntil()).isEqualTo(lockedUntilFirst);
		assertThat(member.getFailedLoginAttempts()).isEqualTo(5);
	}

	@Test
	void a_successful_login_resets_the_counter_and_clears_the_lock() {
		Member member = member();
		for (int i = 0; i < 5; i++) {
			member.recordFailedLogin(NOW, 5, LOCKOUT_DURATION);
		}

		member.recordSuccessfulLogin();

		assertThat(member.getFailedLoginAttempts()).isZero();
		assertThat(member.getLockedUntil()).isNull();
		assertThat(member.isLocked(NOW)).isFalse();
	}
}
