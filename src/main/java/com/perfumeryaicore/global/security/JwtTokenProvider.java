package com.perfumeryaicore.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Access Token(JWT) 발급·검증. Refresh Token은 불투명 토큰으로 별도 관리한다.
 */
@Slf4j
@Component
public class JwtTokenProvider {

	private static final String CLAIM_EMAIL = "email";

	private final SecretKey key;
	private final long accessTokenValiditySeconds;

	public JwtTokenProvider(JwtProperties properties) {
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
		this.accessTokenValiditySeconds = properties.accessTokenValiditySeconds();
	}

	public String createAccessToken(Long memberId, String email) {
		Date now = new Date();
		Date expiry = new Date(now.getTime() + accessTokenValiditySeconds * 1000);
		return Jwts.builder()
				.subject(String.valueOf(memberId))
				.claim(CLAIM_EMAIL, email)
				.issuedAt(now)
				.expiration(expiry)
				.signWith(key)
				.compact();
	}

	/**
	 * 토큰을 검증하고 주체 정보를 추출한다. 유효하지 않으면 {@code null}.
	 */
	public MemberPrincipal parse(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload();
			return new MemberPrincipal(Long.valueOf(claims.getSubject()), claims.get(CLAIM_EMAIL, String.class));
		} catch (JwtException | IllegalArgumentException e) {
			log.debug("Invalid JWT: {}", e.getMessage());
			return null;
		}
	}

	public long getAccessTokenValiditySeconds() {
		return accessTokenValiditySeconds;
	}
}
