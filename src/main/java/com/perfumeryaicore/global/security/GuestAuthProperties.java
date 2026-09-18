package com.perfumeryaicore.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게스트(로그인 없이 체험) 세션 설정 값. {@code security.guest.*} 프로퍼티에 바인딩된다.
 *
 * @param sessionExpiryHours 게스트 세션 유효 기간(시간) - 이 시각 이후 Refresh Token이 만료돼
 *     재로그인이 막힌다. 계정·데이터는 지우지 않는다(영구 보존).
 */
@ConfigurationProperties(prefix = "security.guest")
public record GuestAuthProperties(
		int sessionExpiryHours
) {
}
