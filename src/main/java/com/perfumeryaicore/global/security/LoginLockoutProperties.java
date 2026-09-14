package com.perfumeryaicore.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 실패 횟수 제한(BE-087) 설정 값. {@code security.login.*} 프로퍼티에 바인딩된다.
 *
 * @param maxAttempts            잠금 전까지 허용하는 연속 로그인 실패 횟수
 * @param lockoutDurationSeconds 잠금 지속 시간(초)
 */
@ConfigurationProperties(prefix = "security.login")
public record LoginLockoutProperties(
		int maxAttempts,
		long lockoutDurationSeconds
) {

	public Duration lockoutDuration() {
		return Duration.ofSeconds(lockoutDurationSeconds);
	}
}
