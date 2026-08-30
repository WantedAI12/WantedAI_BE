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
import com.perfumeryaicore.global.security.JwtProperties;
import com.perfumeryaicore.global.security.JwtTokenProvider;
import com.perfumeryaicore.global.security.TokenHasher;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
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

	@Transactional
	public TokenResponse login(LoginRequest request) {
		Member member = memberRepository.findByEmail(request.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
		if (!passwordEncoder.matches(request.password(), member.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		return issueTokens(member.getId(), member.getEmail());
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public TokenResponse refresh(String rawRefreshToken) {
		String hash = tokenHasher.hash(rawRefreshToken);
		RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
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

		stored.revoke(now);
		return issueTokens(member.getId(), member.getEmail());
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		String hash = tokenHasher.hash(rawRefreshToken);
		refreshTokenRepository.findByTokenHash(hash)
				.ifPresent(token -> token.revoke(LocalDateTime.now()));
	}

	private TokenResponse issueTokens(Long memberId, String email) {
		String accessToken = jwtTokenProvider.createAccessToken(memberId, email);
		String rawRefreshToken = generateRawToken();

		refreshTokenRepository.save(RefreshToken.builder()
				.memberId(memberId)
				.tokenHash(tokenHasher.hash(rawRefreshToken))
				.expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.refreshTokenValiditySeconds()))
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
