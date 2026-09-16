package com.perfumeryaicore.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.member.dto.request.LoginRequest;
import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.member.repository.RefreshTokenRepository;
import com.perfumeryaicore.domain.member.service.AuthService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.security.GuestAuthProperties;
import com.perfumeryaicore.global.security.JwtProperties;
import com.perfumeryaicore.global.security.JwtTokenProvider;
import com.perfumeryaicore.global.security.LoginLockoutProperties;
import com.perfumeryaicore.global.security.TokenHasher;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BE-087: {@link AuthService#login}이 실패 횟수를 누적시키고, 잠긴 계정의 로그인을 거부하는지 검증한다.
 */
class AuthServiceLoginLockoutTest {

	private static final String EMAIL = "user@example.com";
	private static final LoginLockoutProperties LOCKOUT_PROPERTIES = new LoginLockoutProperties(5, 900);

	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final TokenHasher tokenHasher = mock(TokenHasher.class);
	private final JwtProperties jwtProperties = new JwtProperties("test-secret-value-longer-than-32-bytes-000", 3600, 1209600);
	private final AuthService service = new AuthService(memberRepository, refreshTokenRepository,
			passwordEncoder, jwtTokenProvider, tokenHasher, jwtProperties, LOCKOUT_PROPERTIES,
			new GuestAuthProperties(24));

	private static Member member() {
		Member member = Member.builder().email(EMAIL).passwordHash("hash").name("사용자").build();
		try {
			Field field = Member.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(member, 1L);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return member;
	}

	@Test
	void a_wrong_password_increments_the_failed_attempt_counter() {
		Member member = member();
		when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

		assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "wrong")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);

		assertThat(member.getFailedLoginAttempts()).isEqualTo(1);
	}

	@Test
	void the_fifth_wrong_password_locks_the_account() {
		Member member = member();
		when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "wrong")))
					.isInstanceOf(BusinessException.class);
		}

		assertThat(member.isLocked(java.time.LocalDateTime.now())).isTrue();
	}

	@Test
	void a_locked_account_is_rejected_even_with_the_correct_password() {
		Member member = member();
		member.recordFailedLogin(java.time.LocalDateTime.now(), 5, java.time.Duration.ofMinutes(15));
		member.recordFailedLogin(java.time.LocalDateTime.now(), 5, java.time.Duration.ofMinutes(15));
		member.recordFailedLogin(java.time.LocalDateTime.now(), 5, java.time.Duration.ofMinutes(15));
		member.recordFailedLogin(java.time.LocalDateTime.now(), 5, java.time.Duration.ofMinutes(15));
		member.recordFailedLogin(java.time.LocalDateTime.now(), 5, java.time.Duration.ofMinutes(15));
		when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

		assertThatThrownBy(() -> service.login(new LoginRequest(EMAIL, "correct")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_LOCKED);
	}

	@Test
	void a_successful_login_resets_a_previously_accumulated_failure_count() {
		Member member = member();
		member.recordFailedLogin(java.time.LocalDateTime.now(), 5, java.time.Duration.ofMinutes(15));
		member.recordFailedLogin(java.time.LocalDateTime.now(), 5, java.time.Duration.ofMinutes(15));
		when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
		when(jwtTokenProvider.createAccessToken(anyLong(), anyString())).thenReturn("access-token");
		when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(3600L);

		service.login(new LoginRequest(EMAIL, "correct"));

		assertThat(member.getFailedLoginAttempts()).isZero();
	}
}
