package com.perfumeryaicore.domain.member.service;

import com.perfumeryaicore.domain.member.repository.PasswordResetTokenRepository;
import com.perfumeryaicore.domain.member.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 RefreshToken/PasswordResetToken을 주기적으로 정리한다(BE-107). 폐기(revoke) 여부와
 * 무관하게 만료 시각만 기준으로 삭제 대상을 고른다 - 재사용 탐지로 폐기된 토큰도 만료 전까지는
 * "탈취된 토큰이 다시 쓰였는지" 조사 대상이 될 수 있어 곧바로 지우지 않고, 만료 후에도
 * {@link #RETENTION_DAYS_AFTER_EXPIRY}만큼 더 남겨둔다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenCleanupService {

	private static final int RETENTION_DAYS_AFTER_EXPIRY = 7;

	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordResetTokenRepository passwordResetTokenRepository;

	/** 매일 새벽 4시(서버 시간대) 실행. 실제 운영 트래픽이 가장 적을 시간대라는 가정 - 확인 전까지 임의값. */
	@Scheduled(cron = "0 0 4 * * *")
	@Transactional
	public void purgeExpiredTokens() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS_AFTER_EXPIRY);
		int refreshDeleted = refreshTokenRepository.deleteByExpiresAtBefore(cutoff);
		int resetDeleted = passwordResetTokenRepository.deleteByExpiresAtBefore(cutoff);
		log.info("[AUTH] purged expired tokens refreshTokens={} passwordResetTokens={} cutoff={}",
				refreshDeleted, resetDeleted, cutoff);
	}
}
