package com.perfumeryaicore.domain.member.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

	@Builder
	private Member(String email, String passwordHash, String name) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.name = name;
	}

	public void updateName(String name) {
		this.name = name;
	}

	public void updatePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}
}
