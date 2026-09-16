package com.perfumeryaicore.domain.member.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Refresh Token. 원문이 아닌 SHA-256 해시값만 저장한다.
 * 회전(rotation) 시 기존 토큰의 {@code revokedAt}을 채우고 새 토큰을 발급하며,
 * 이미 폐기된 토큰이 재사용되면 해당 회원의 모든 토큰을 강제 폐기한다.
 */
@Entity
@Getter
@Table(
		name = "refresh_tokens",
		indexes = @Index(name = "idx_refresh_tokens_member_id", columnList = "member_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "revoked_at")
	private LocalDateTime revokedAt;

	@Builder
	private RefreshToken(Long memberId, String tokenHash, LocalDateTime expiresAt) {
		this.memberId = memberId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(LocalDateTime now) {
		return expiresAt.isBefore(now);
	}

	public boolean isActive(LocalDateTime now) {
		return !isRevoked() && !isExpired(now);
	}

	public void revoke(LocalDateTime now) {
		if (revokedAt == null) {
			this.revokedAt = now;
		}
	}
}
