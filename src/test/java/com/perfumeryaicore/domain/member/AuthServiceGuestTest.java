package com.perfumeryaicore.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.entity.RefreshToken;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.member.repository.RefreshTokenRepository;
import com.perfumeryaicore.domain.member.service.AuthService;
import com.perfumeryaicore.global.security.JwtProperties;
import com.perfumeryaicore.global.security.JwtTokenProvider;
import com.perfumeryaicore.global.security.LoginLockoutProperties;
import com.perfumeryaicore.global.security.TokenHasher;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 게스트 모드: 가입 없이 즉시 회원처럼 쓸 수 있는 임시 계정을 만든다. 일반 회원과 동일하게
 * 무기한 유지된다 - 만료·로그아웃·데이터 삭제 없음(게스트 사용 데이터도 계속 수집하기로 결정).
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
		ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
		when(refreshTokenRepository.save(tokenCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

		var response = service.guestLogin();

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(memberCaptor.getValue().isGuest()).isTrue();
		// 일반 회원과 같은 refresh 유효기간(테스트 설정 기준 14일)을 그대로 쓴다 - 캡핑 없음.
		assertThat(tokenCaptor.getValue().getExpiresAt())
				.isAfter(java.time.LocalDateTime.now().plusDays(13));
	}

	@Test
	void createGuest_produces_a_member_with_a_placeholder_email_and_no_real_password() {
		when(passwordEncoder.encode(anyString())).thenReturn("hash");

		Member guest = Member.createGuest("guest+x@guest.perfumery.local", passwordEncoder.encode("random"));

		assertThat(guest.isGuest()).isTrue();
		assertThat(guest.getEmail()).contains("guest");
	}
}
