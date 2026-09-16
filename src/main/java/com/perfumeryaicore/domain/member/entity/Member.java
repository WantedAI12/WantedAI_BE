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
	 * 게스트 모드(로그인 없이 체험) 계정 표시. 일반 회원과 동일하게 무기한 유지되고 데이터도
	 * 계속 쌓인다 - 이 플래그는 만료·삭제 판단용이 아니라 게스트 출처 데이터를 구분하기 위한
	 * 것이다(예: AI 학습·분석용 데이터 수집).
	 */
	@Column(name = "is_guest", nullable = false)
	private boolean guest;

	@Builder
	private Member(String email, String passwordHash, String name) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.name = name;
	}

	private Member(String email, String passwordHash, String name, boolean guest) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.name = name;
		this.guest = guest;
	}

	/** 임의의 무작위 비밀번호 해시로 즉시 사용 가능한 게스트 계정을 만든다 - 실제 로그인 수단은 없다. */
	public static Member createGuest(String email, String passwordHash) {
		return new Member(email, passwordHash, "Guest", true);
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
