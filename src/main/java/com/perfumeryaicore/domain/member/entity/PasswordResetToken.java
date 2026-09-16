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
 * 비로그인 비밀번호 재설정 토큰(COR-B03). {@link RefreshToken}과 같은 이유로 원문이 아닌
 * SHA-256 해시값만 저장한다 — DB가 유출돼도 토큰 자체를 재구성할 수 없게 하기 위함.
 *
 * <p>1회용이다: 사용(재설정 완료)하거나 새 요청으로 대체되면 {@code revokedAt}을 채운다.
 */
@Entity
@Getter
@Table(
		name = "password_reset_tokens",
		indexes = @Index(name = "idx_password_reset_tokens_member_id", columnList = "member_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PasswordResetToken extends BaseTimeEntity {

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
	private PasswordResetToken(Long memberId, String tokenHash, LocalDateTime expiresAt) {
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
}
