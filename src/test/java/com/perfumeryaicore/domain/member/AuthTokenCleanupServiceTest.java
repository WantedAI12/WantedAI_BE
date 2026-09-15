package com.perfumeryaicore.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.member.repository.PasswordResetTokenRepository;
import com.perfumeryaicore.domain.member.repository.RefreshTokenRepository;
import com.perfumeryaicore.domain.member.service.AuthTokenCleanupService;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** BE-107: 만료된 지 오래된 인증 토큰을 정리한다. */
class AuthTokenCleanupServiceTest {

	private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
	private final PasswordResetTokenRepository passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
	private final AuthTokenCleanupService service =
			new AuthTokenCleanupService(refreshTokenRepository, passwordResetTokenRepository);

	@Test
	void purgeExpiredTokens_deletes_rows_expired_more_than_the_retention_window_ago() {
		when(refreshTokenRepository.deleteByExpiresAtBefore(any())).thenReturn(3);
		when(passwordResetTokenRepository.deleteByExpiresAtBefore(any())).thenReturn(1);

		service.purgeExpiredTokens();

		ArgumentCaptor<LocalDateTime> refreshCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
		ArgumentCaptor<LocalDateTime> resetCutoff = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(refreshTokenRepository).deleteByExpiresAtBefore(refreshCutoff.capture());
		verify(passwordResetTokenRepository).deleteByExpiresAtBefore(resetCutoff.capture());

		LocalDateTime expectedAround = LocalDateTime.now().minusDays(7);
		assertThat(refreshCutoff.getValue()).isCloseTo(expectedAround, within(Duration.ofMinutes(1)));
		assertThat(resetCutoff.getValue()).isEqualTo(refreshCutoff.getValue());
	}
}
