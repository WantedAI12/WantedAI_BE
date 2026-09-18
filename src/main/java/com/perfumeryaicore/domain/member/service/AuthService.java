package com.perfumeryaicore.domain.member.service;

import com.perfumeryaicore.domain.member.dto.request.LoginRequest;
import com.perfumeryaicore.domain.member.dto.request.SignupRequest;
import com.perfumeryaicore.domain.member.dto.response.MemberResponse;
import com.perfumeryaicore.domain.member.dto.response.TokenResponse;
import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.entity.RefreshToken;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.member.repository.RefreshTokenRepository;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.security.GuestAuthProperties;
import com.perfumeryaicore.global.security.JwtProperties;
import com.perfumeryaicore.global.security.JwtTokenProvider;
import com.perfumeryaicore.global.security.LoginLockoutProperties;
import com.perfumeryaicore.global.security.TokenHasher;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 / 로그인 / 토큰 재발급 / 로그아웃.
 *
 * <p>Access Token은 무상태 JWT, Refresh Token은 불투명 토큰이며 DB에는 SHA-256 해시만 저장한다.
 * 재발급 시 기존 토큰을 폐기하고 새로 발급(rotation)하며, 이미 폐기된 토큰이 다시 들어오면
 * 탈취로 간주해 해당 회원의 모든 Refresh Token을 폐기한다(reuse detection).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private static final int RAW_TOKEN_BYTES = 32;

	private final MemberRepository memberRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final TokenHasher tokenHasher;
	private final JwtProperties jwtProperties;
	private final LoginLockoutProperties loginLockoutProperties;
	private final GuestAuthProperties guestAuthProperties;
	private final SecureRandom secureRandom = new SecureRandom();

	@Transactional
	public MemberResponse signup(SignupRequest request) {
		if (memberRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
		}
		Member member = memberRepository.save(Member.builder()
				.email(request.email())
				.passwordHash(passwordEncoder.encode(request.password()))
				.name(request.name())
				.build());
		return MemberResponse.from(member);
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public TokenResponse login(LoginRequest request) {
		Member member = memberRepository.findByEmail(request.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

		LocalDateTime now = LocalDateTime.now();
		if (member.isLocked(now)) {
			throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
		}
		if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
			member.recordFailedLogin(now, loginLockoutProperties.maxAttempts(),
					loginLockoutProperties.lockoutDuration());
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		member.recordSuccessfulLogin();
		return issueTokens(member.getId(), member.getEmail());
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public TokenResponse refresh(String rawRefreshToken) {
		String hash = tokenHasher.hash(rawRefreshToken);
		// BE-011: 잠금 조회로 동시 회전 요청을 직렬화한다 — 자세한 이유는 리포지토리 쪽 주석 참고.
		RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash)
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

		LocalDateTime now = LocalDateTime.now();

		if (stored.isRevoked()) {
			// 폐기된 토큰 재사용 → 탈취 가능성. 해당 회원의 모든 토큰 폐기.
			refreshTokenRepository.revokeAllByMemberId(stored.getMemberId(), now);
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
		}
		if (stored.isExpired(now)) {
			stored.revoke(now);
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		Member member = memberRepository.findById(stored.getMemberId())
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (member.isGuestExpired(now)) {
			throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
		}

		stored.revoke(now);
		// 게스트는 회전해도 원래 만료 시각을 넘길 수 없다 - 캡을 매번 다시 걸지 않으면
		// refresh를 반복 호출해 세션을 무기한 연장할 수 있다.
		return issueTokens(member.getId(), member.getEmail(), member.isGuest() ? member.getGuestExpiresAt() : null);
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		String hash = tokenHasher.hash(rawRefreshToken);
		refreshTokenRepository.findByTokenHash(hash)
				.ifPresent(token -> token.revoke(LocalDateTime.now()));
	}

	/**
	 * 게스트 모드(2026-09-18 재조정): 가입 없이 즉시 회원처럼 쓸 수 있는 임시 계정을 만들고 바로
	 * 로그인 상태로 토큰을 발급한다. 이 계정이 만드는 프로젝트에 자동으로 PERFUMER가 되는 것
	 * 외에는 다른 도메인 코드가 전혀 게스트 여부를 구분하지 않는다(권한 체계가 프로젝트 단위라서
	 * 그대로 작동한다, {@code ProjectService.initialRoleFor} 참고).
	 *
	 * <p>세션은 {@code sessionExpiryHours} 뒤에 리셋된다(Refresh Token 재발급이 막혀 다시
	 * 게스트로 시작해야 함) - 나갔다 다시 들어오면 처음 상태로 보여야 한다는 요구사항 때문이다.
	 * 계정·데이터(프로젝트·후보 등)는 세션 만료와 무관하게 절대 지우지 않는다 - AI 학습·분석용으로
	 * 계속 수집한다(2026-09-17 결정, 데이터 보존과 세션 리셋은 서로 다른 축이다).
	 */
	@Transactional
	public TokenResponse guestLogin() {
		LocalDateTime expiresAt = LocalDateTime.now().plusHours(guestAuthProperties.sessionExpiryHours());
		Member guest = memberRepository.save(Member.createGuest(
				"guest+" + UUID.randomUUID() + "@guest.perfumery.local",
				passwordEncoder.encode(generateRawToken()), expiresAt));
		return issueTokens(guest.getId(), guest.getEmail(), expiresAt);
	}

	private TokenResponse issueTokens(Long memberId, String email) {
		return issueTokens(memberId, email, null);
	}

	private TokenResponse issueTokens(Long memberId, String email, LocalDateTime maxRefreshExpiry) {
		String accessToken = jwtTokenProvider.createAccessToken(memberId, email);
		String rawRefreshToken = generateRawToken();

		LocalDateTime refreshExpiry = LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenValiditySeconds());
		if (maxRefreshExpiry != null && maxRefreshExpiry.isBefore(refreshExpiry)) {
			refreshExpiry = maxRefreshExpiry;
		}

		refreshTokenRepository.save(RefreshToken.builder()
				.memberId(memberId)
				.tokenHash(tokenHasher.hash(rawRefreshToken))
				.expiresAt(refreshExpiry)
				.build());

		return new TokenResponse(accessToken, rawRefreshToken,
				jwtTokenProvider.getAccessTokenValiditySeconds());
	}

	private String generateRawToken() {
		byte[] bytes = new byte[RAW_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
