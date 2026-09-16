package com.perfumeryaicore.domain.member.repository;

import com.perfumeryaicore.domain.member.entity.PasswordResetToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	/** 같은 회원이 쿨다운 시간 내에 이미 유효한 토큰을 발급받았는지(간단한 요청 제한용). */
	boolean existsByMemberIdAndRevokedAtIsNullAndCreatedAtAfter(Long memberId, LocalDateTime after);

	/**
	 * 해당 회원의 아직 폐기되지 않은 모든 재설정 토큰을 일괄 폐기한다.
	 * (재설정 완료 시 방금 쓴 토큰 포함 나머지 미사용 토큰까지 함께 무효화 — 1회용 보장)
	 */
	@Modifying(clearAutomatically = true)
	@Query("update PasswordResetToken t set t.revokedAt = :now "
			+ "where t.memberId = :memberId and t.revokedAt is null")
	int revokeAllByMemberId(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

	/** 만료된 지 오래된 토큰을 정리한다(BE-107) - 폐기 여부와 무관하게 {@code cutoff} 이전에 만료된 행 전부. */
	@Modifying(clearAutomatically = true)
	@Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
	int deleteByExpiresAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
