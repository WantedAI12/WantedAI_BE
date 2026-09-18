package com.perfumeryaicore.domain.member.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 회원. 자체 로그인(이메일 + BCrypt 해시 비밀번호).
 */
@Entity
@Getter
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "locked_until")
	private LocalDateTime lockedUntil;

	/**
	 * 게스트 모드(로그인 없이 체험) 계정 표시 - 게스트 출처 데이터를 구분하기 위한 플래그다.
	 * 이 계정으로 만든 프로젝트·후보 등 모든 데이터는 만료 후에도 지우지 않고 영구 보존한다
	 * (AI 학습·분석용, 2026-09-17 결정) - {@link #guestExpiresAt}은 데이터 삭제 기준이 아니라
	 * "세션(로그인 상태)"만 리셋하는 기준이다: 이 시각이 지나면 Refresh Token 재발급이 막혀
	 * 다시 게스트로 시작해야 하지만, 이전 계정과 데이터는 그대로 남아 있다.
	 */
	@Column(name = "is_guest", nullable = false)
	private boolean guest;

	/** 게스트 세션 만료 시각(로그인 유지 한도) - 일반 회원은 항상 {@code null}. */
	@Column(name = "guest_expires_at")
	private LocalDateTime guestExpiresAt;

	@Builder
	private Member(String email, String passwordHash, String name) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.name = name;
	}

	private Member(String email, String passwordHash, String name, boolean guest, LocalDateTime guestExpiresAt) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.name = name;
		this.guest = guest;
		this.guestExpiresAt = guestExpiresAt;
	}

	/**
	 * 임의의 무작위 비밀번호 해시로 즉시 사용 가능한 게스트 계정을 만든다 - 실제 로그인 수단은
	 * 없다. {@code expiresAt} 이후엔 세션(로그인)만 끊긴다 - 계정과 데이터는 지우지 않는다.
	 */
	public static Member createGuest(String email, String passwordHash, LocalDateTime expiresAt) {
		return new Member(email, passwordHash, "Guest", true, expiresAt);
	}

	public boolean isGuestExpired(LocalDateTime now) {
		return guestExpiresAt != null && now.isAfter(guestExpiresAt);
	}

	public void updateName(String name) {
		this.name = name;
	}

	public void updatePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public boolean isLocked(LocalDateTime now) {
		return lockedUntil != null && now.isBefore(lockedUntil);
	}

	/**
	 * BE-087: 로그인 실패를 기록한다. 실패 횟수가 임계치에 도달하면 계정을 잠근다.
	 * (잠금 중에 다시 실패해도 잠금 해제 시각은 뒤로 미루지 않는다 - 무한 잠금 연장 방지)
	 */
	public void recordFailedLogin(LocalDateTime now, int maxAttempts, Duration lockoutDuration) {
		if (isLocked(now)) {
			return;
		}
		this.failedLoginAttempts++;
		if (this.failedLoginAttempts >= maxAttempts) {
			this.lockedUntil = now.plus(lockoutDuration);
		}
	}

	public void recordSuccessfulLogin() {
		this.failedLoginAttempts = 0;
		this.lockedUntil = null;
	}
}
