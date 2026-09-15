package com.perfumeryaicore.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.entity.PasswordResetToken;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.member.repository.PasswordResetTokenRepository;
import com.perfumeryaicore.domain.member.repository.RefreshTokenRepository;
import com.perfumeryaicore.domain.member.service.PasswordResetService;
import com.perfumeryaicore.global.config.AppProperties;
import com.perfumeryaicore.global.email.EmailSender;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.security.TokenHasher;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비로그인 비밀번호 재설정(COR-B03): 계정 존재 비노출, 쿨다운, 1회용 토큰, 재설정 후 세션 전체 종료를 검증한다.
 */
class PasswordResetServiceTest {

	private static final long MEMBER_ID = 1L;
	private static final String RAW_TOKEN = "raw-token-value";

	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final PasswordResetTokenRepository resetTokenRepository = mock(PasswordResetTokenRepository.class);
	private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final TokenHasher tokenHasher = mock(TokenHasher.class);
	private final EmailSender emailSender = mock(EmailSender.class);
	private final AppProperties appProperties = new AppProperties("http://localhost:3000");
	private final PasswordResetService service = new PasswordResetService(
			memberRepository, resetTokenRepository, refreshTokenRepository, passwordEncoder, tokenHasher,
			emailSender, appProperties);

	private static Member member(long id) {
		Member m = Member.builder().email("user@example.com").passwordHash("old-hash").name("사용자").build();
		try {
			Field field = Member.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(m, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return m;
	}

	private static PasswordResetToken token(long memberId, LocalDateTime expiresAt) {
		return PasswordResetToken.builder()
				.memberId(memberId)
				.tokenHash("hashed")
				.expiresAt(expiresAt)
				.build();
	}

	@Test
	void forgotPassword_for_an_unknown_email_silently_does_nothing() {
		when(memberRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

		service.forgotPassword("ghost@example.com");

		verify(resetTokenRepository, never()).save(any());
		verify(emailSender, never()).send(any(), any(), any());
	}

	@Test
	void forgotPassword_for_a_known_email_issues_a_token_and_emails_the_reset_link() {
		when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(member(MEMBER_ID)));
		when(resetTokenRepository.existsByMemberIdAndRevokedAtIsNullAndCreatedAtAfter(eq(MEMBER_ID), any()))
				.thenReturn(false);

		service.forgotPassword("user@example.com");

		ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
		verify(resetTokenRepository).save(captor.capture());
		assertThat(captor.getValue().getMemberId()).isEqualTo(MEMBER_ID);

		ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
		verify(emailSender).send(eq("user@example.com"), any(), bodyCaptor.capture());
		assertThat(bodyCaptor.getValue()).contains("http://localhost:3000/reset-password?token=");
	}

	@Test
	void forgotPassword_within_the_cooldown_window_does_not_issue_a_second_token_or_email() {
		when(memberRepository.findByEmail("user@example.com")).thenReturn(Optional.of(member(MEMBER_ID)));
		when(resetTokenRepository.existsByMemberIdAndRevokedAtIsNullAndCreatedAtAfter(eq(MEMBER_ID), any()))
				.thenReturn(true);

		service.forgotPassword("user@example.com");

		verify(resetTokenRepository, never()).save(any());
		verify(emailSender, never()).send(any(), any(), any());
	}

	@Test
	void resetPassword_with_an_unknown_token_is_rejected() {
		when(tokenHasher.hash(RAW_TOKEN)).thenReturn("hashed");
		when(resetTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, "new-password-123"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
	}

	@Test
	void resetPassword_with_an_expired_token_is_rejected() {
		when(tokenHasher.hash(RAW_TOKEN)).thenReturn("hashed");
		when(resetTokenRepository.findByTokenHash("hashed"))
				.thenReturn(Optional.of(token(MEMBER_ID, LocalDateTime.now().minusMinutes(1))));

		assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, "new-password-123"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
		verify(memberRepository, never()).findById(anyLong());
	}

	@Test
	void resetPassword_succeeds_updates_password_and_revokes_all_sessions() {
		PasswordResetToken stored = token(MEMBER_ID, LocalDateTime.now().plusMinutes(10));
		when(tokenHasher.hash(RAW_TOKEN)).thenReturn("hashed");
		when(resetTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.of(stored));
		when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member(MEMBER_ID)));
		when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

		service.resetPassword(RAW_TOKEN, "new-password-123");

		verify(resetTokenRepository).revokeAllByMemberId(eq(MEMBER_ID), any());
		verify(refreshTokenRepository).revokeAllByMemberId(eq(MEMBER_ID), any());
	}

	@Test
	void resetPassword_with_an_already_used_token_is_rejected() {
		PasswordResetToken stored = token(MEMBER_ID, LocalDateTime.now().plusMinutes(10));
		// 이미 사용(폐기)된 토큰 상태를 흉내낸다.
		try {
			Field field = PasswordResetToken.class.getDeclaredField("revokedAt");
			field.setAccessible(true);
			field.set(stored, LocalDateTime.now().minusMinutes(1));
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		when(tokenHasher.hash(RAW_TOKEN)).thenReturn("hashed");
		when(resetTokenRepository.findByTokenHash("hashed")).thenReturn(Optional.of(stored));

		assertThatThrownBy(() -> service.resetPassword(RAW_TOKEN, "new-password-123"))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
		verify(refreshTokenRepository, never()).revokeAllByMemberId(anyLong(), any());
	}
}
