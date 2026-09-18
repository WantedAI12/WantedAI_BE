package com.perfumeryaicore.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.entity.RefreshToken;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.member.repository.RefreshTokenRepository;
import com.perfumeryaicore.domain.member.service.AuthService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.security.JwtProperties;
import com.perfumeryaicore.global.security.JwtTokenProvider;
import com.perfumeryaicore.global.security.LoginLockoutProperties;
import com.perfumeryaicore.global.security.TokenHasher;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 게스트 모드: 가입 없이 즉시 회원처럼 쓸 수 있는 임시 계정을 만든다. 계정·데이터는 일반 회원과
 * 같이 영구 보존하지만(삭제 없음, AI 학습·분석용으로 계속 수집), 세션은 재발급(refresh)을
 * 허용하지 않아 Access Token이 만료되는 순간(나갔다 들어오는 등) 끝난다 - 다시 쓰려면 반드시 새
 * guestLogin()을 호출해야 한다("나갔다 들어오면 초기화", 2026-09-18 결정).
 */
class AuthServiceGuestTest {

	private static final JwtProperties JWT_PROPERTIES =
			new JwtProperties("test-secret-value-longer-than-32-bytes-000", 3600, 1209600);

	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final TokenHasher tokenHasher = mock(TokenHasher.class);
	private final AuthService service = new AuthService(memberRepository, refreshTokenRepository,
			passwordEncoder, jwtTokenProvider, tokenHasher, JWT_PROPERTIES,
			new LoginLockoutProperties(5, 900));

	private static void setId(Member member, long id) {
		try {
			Field field = Member.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(member, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void guestLogin_creates_a_guest_member_and_issues_tokens_like_a_normal_login() {
		ArgumentCaptor<Member> memberCaptor = ArgumentCaptor.forClass(Member.class);
		when(memberRepository.save(memberCaptor.capture())).thenAnswer(inv -> {
			Member guest = inv.getArgument(0);
			setId(guest, 900L);
			return guest;
		});
		when(passwordEncoder.encode(anyString())).thenReturn("hash");
		when(jwtTokenProvider.createAccessToken(anyLong(), anyString())).thenReturn("access-token");
		when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(3600L);
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

		var response = service.guestLogin();

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(memberCaptor.getValue().isGuest()).isTrue();
	}

	@Test
	void createGuest_produces_a_member_with_a_placeholder_email_and_no_real_password() {
		when(passwordEncoder.encode(anyString())).thenReturn("hash");

		Member guest = Member.createGuest("guest+x@guest.perfumery.local", passwordEncoder.encode("random"));

		assertThat(guest.isGuest()).isTrue();
		assertThat(guest.getEmail()).contains("guest");
	}

	@Test
	void refresh_always_rejects_a_guest_member_so_the_session_resets_on_the_next_visit() {
		Member guest = Member.createGuest("guest+y@guest.perfumery.local", "hash");
		setId(guest, 901L);
		RefreshToken stored = RefreshToken.builder()
				.memberId(901L).tokenHash("hash").expiresAt(LocalDateTime.now().plusDays(1)).build();
		when(tokenHasher.hash(anyString())).thenReturn("hash");
		when(refreshTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(stored));
		when(memberRepository.findById(901L)).thenReturn(Optional.of(guest));

		assertThatThrownBy(() -> service.refresh("raw-token"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
		assertThat(stored.isRevoked()).isTrue();
	}
}
