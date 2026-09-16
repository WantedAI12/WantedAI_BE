package com.perfumeryaicore.domain.member.service;

import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.entity.PasswordResetToken;
import com.perfumeryaicore.domain.member.entity.RefreshToken;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.member.repository.PasswordResetTokenRepository;
import com.perfumeryaicore.domain.member.repository.RefreshTokenRepository;
import com.perfumeryaicore.global.config.AppProperties;
import com.perfumeryaicore.global.email.EmailSender;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.security.TokenHasher;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비로그인 비밀번호 재설정(COR-B03). {@link RefreshToken}과 같은 방식으로 원문이 아닌 해시만 저장하고,
 * 계정 존재 여부를 노출하지 않도록 이메일 요청은 가입 여부와 무관하게 항상 같은 방식으로 끝난다.
 *
 * <p>{@link com.perfumeryaicore.global.email.EmailSender} 발송 실패는 이 원칙을 지키기 위해
 * 예외를 던지지 않는다 — 발송 성공 여부로 API 응답이 달라지면 그 자체로 가입 여부를 추측할 수
 * 있는 신호가 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

	private static final int RAW_TOKEN_BYTES = 32;
	private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
	/** 같은 회원이 이 시간 내에 다시 요청하면 새 토큰을 발급하지 않는다(간단한 요청 제한). */
	private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);

	private final MemberRepository memberRepository;
	private final PasswordResetTokenRepository resetTokenRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenHasher tokenHasher;
	private final EmailSender emailSender;
	private final AppProperties appProperties;
	private final SecureRandom secureRandom = new SecureRandom();

	/**
	 * 이메일이 실제로 가입돼 있는지와 무관하게 항상 같은 방식으로 끝난다 — 응답만으로 계정 존재
	 * 여부를 추측할 수 없게 한다. 존재하지 않는 이메일은 조용히 아무 일도 하지 않는다.
	 */
	@Transactional
	public void forgotPassword(String email) {
		memberRepository.findByEmail(email).ifPresent(this::issueTokenUnlessCoolingDown);
	}

	private void issueTokenUnlessCoolingDown(Member member) {
		LocalDateTime now = LocalDateTime.now();
		if (resetTokenRepository.existsByMemberIdAndRevokedAtIsNullAndCreatedAtAfter(
				member.getId(), now.minus(REQUEST_COOLDOWN))) {
			log.info("[AUTH] password reset request throttled for member={}", member.getId());
			return;
		}
		String rawToken = generateRawToken();
		resetTokenRepository.save(PasswordResetToken.builder()
				.memberId(member.getId())
				.tokenHash(tokenHasher.hash(rawToken))
				.expiresAt(now.plus(TOKEN_TTL))
				.build());
		log.info("[AUTH] password reset token issued for member={}", member.getId());

		String resetLink = "%s/reset-password?token=%s".formatted(appProperties.frontendBaseUrl(), rawToken);
		emailSender.send(member.getEmail(), "비밀번호 재설정 안내",
				"비밀번호를 재설정하려면 아래 링크를 30분 이내에 열어주세요:\n\n" + resetLink
						+ "\n\n본인이 요청하지 않았다면 이 메일을 무시하세요.");
	}

	/**
	 * 토큰이 유효하지 않거나(없음/만료/이미 사용) 하면 {@link ErrorCode#PASSWORD_RESET_TOKEN_INVALID}.
	 * 성공하면 이 토큰을 포함해 해당 회원의 다른 미사용 재설정 토큰도 모두 무효화하고(1회용 보장),
	 * 기존 Refresh Token도 전부 폐기한다(재설정 후 기존 세션 전체 종료).
	 */
	@Transactional
	public void resetPassword(String rawToken, String newPassword) {
		String hash = tokenHasher.hash(rawToken);
		PasswordResetToken stored = resetTokenRepository.findByTokenHash(hash)
				.orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

		LocalDateTime now = LocalDateTime.now();
		if (!stored.isActive(now)) {
			throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
		}

		Member member = memberRepository.findById(stored.getMemberId())
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

		member.updatePassword(passwordEncoder.encode(newPassword));
		resetTokenRepository.revokeAllByMemberId(member.getId(), now);
		refreshTokenRepository.revokeAllByMemberId(member.getId(), now);
		log.info("[AUTH] password reset completed for member={}", member.getId());
	}

	private String generateRawToken() {
		byte[] bytes = new byte[RAW_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
