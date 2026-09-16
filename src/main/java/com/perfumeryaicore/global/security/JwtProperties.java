package com.perfumeryaicore.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 값. {@code jwt.*} 프로퍼티에 바인딩된다.
 *
 * @param secret                      HS256 서명 키 (최소 32바이트)
 * @param accessTokenValiditySeconds  Access Token 유효 기간(초)
 * @param refreshTokenValiditySeconds Refresh Token 유효 기간(초)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
		String secret,
		long accessTokenValiditySeconds,
		long refreshTokenValiditySeconds
) {
}
