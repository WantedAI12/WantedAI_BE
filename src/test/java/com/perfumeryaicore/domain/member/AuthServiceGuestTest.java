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
import com.perfumeryaicore.global.security.GuestAuthProperties;
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

/** 게스트 모드: 가입 없이 즉시 회원처럼 쓸 수 있는 임시 계정과 세션 만료 캡핑을 검증한다. */
class AuthServiceGuestTest {

	private static final GuestAuthProperties GUEST_PROPERTIES = new GuestAuthProperties(24);
	private static final JwtProperties JWT_PROPERTIES =
			new JwtProperties("test-secret-value-longer-than-32-bytes-000", 3600, 1209600);

	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final TokenHasher tokenHasher = mock(TokenHasher.class);
	private final AuthService service = new AuthService(memberRepository, refreshTokenRepository,
			passwordEncoder, jwtTokenProvider, tokenHasher, JWT_PROPERTIES,
			new LoginLockoutProperties(5, 900), GUEST_PROPERTIES);

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
	void guestLogin_creates_a_guest_member_and_caps_the_refresh_token_to_the_session_expiry() {
		when(memberRepository.save(any(Member.class))).thenAnswer(inv -> {
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
		LocalDateTime capped = tokenCaptor.getValue().getExpiresAt();
		// 일반 회원의 refresh 유효기간(14일)보다 훨씬 짧게, 게스트 세션 만료(24시간)로 캡핑돼야 한다.
		assertThat(capped).isBefore(LocalDateTime.now().plusDays(1).plusMinutes(1));
	}

	@Test
	void refresh_rejects_a_refresh_token_whose_guest_session_already_expired() {
		Member expiredGuest = Member.createGuest(
				"guest+x@guest.perfumery.local", "hash", LocalDateTime.now().minusMinutes(1));
		setId(expiredGuest, 901L);
		RefreshToken stored = RefreshToken.builder()
				.memberId(901L).tokenHash("hash").expiresAt(LocalDateTime.now().plusDays(1)).build();
		when(tokenHasher.hash(anyString())).thenReturn("hash");
		when(refreshTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(stored));
		when(memberRepository.findById(901L)).thenReturn(Optional.of(expiredGuest));

		assertThatThrownBy(() -> service.refresh("raw-token"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
	}

	@Test
	void refresh_recaps_the_reissued_token_to_the_original_guest_expiry() {
		LocalDateTime guestExpiry = LocalDateTime.now().plusHours(2);
		Member guest = Member.createGuest("guest+y@guest.perfumery.local", "hash", guestExpiry);
		setId(guest, 902L);
		RefreshToken stored = RefreshToken.builder()
				.memberId(902L).tokenHash("hash").expiresAt(guestExpiry).build();
		when(tokenHasher.hash(anyString())).thenReturn("hash");
		when(refreshTokenRepository.findByTokenHashForUpdate("hash")).thenReturn(Optional.of(stored));
		when(memberRepository.findById(902L)).thenReturn(Optional.of(guest));
		when(jwtTokenProvider.createAccessToken(anyLong(), anyString())).thenReturn("access-token");
		when(jwtTokenProvider.getAccessTokenValiditySeconds()).thenReturn(3600L);
		ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
		when(refreshTokenRepository.save(tokenCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

		service.refresh("raw-token");

		// 재발급된 토큰도 원래 게스트 만료 시각을 넘지 않아야 한다 - 반복 refresh로 세션을
		// 무기한 연장할 수 없다.
		assertThat(tokenCaptor.getValue().getExpiresAt()).isBeforeOrEqualTo(guestExpiry);
	}
}
