package com.perfumeryaicore.domain.member.dto.response;

/**
 * 로그인 / 토큰 재발급 응답.
 *
 * @param accessToken  JWT Access Token
 * @param refreshToken 불투명 Refresh Token 원문 (서버에는 해시만 저장)
 * @param expiresIn    Access Token 유효 기간(초)
 */
public record TokenResponse(
		String accessToken,
		String refreshToken,
		long expiresIn
) {
}
