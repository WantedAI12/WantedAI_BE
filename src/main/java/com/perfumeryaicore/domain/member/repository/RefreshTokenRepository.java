package com.perfumeryaicore.domain.member.repository;

import com.perfumeryaicore.domain.member.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * 토큰 회전(rotation) 중 재사용 판정을 위해 해당 토큰 행을 잠그고 조회한다(BE-011).
	 *
	 * <p>같은 refresh token으로 두 요청이 거의 동시에 들어와도, 이 조회부터 트랜잭션이 끝날
	 * 때까지 잠금이 유지되므로 하나가 먼저 커밋(폐기)된 뒤에야 나머지가 최신 상태(폐기됨)를
	 * 읽는다 — 두 요청 모두 "아직 폐기 안 됨"으로 읽고 통과하는 경쟁 조건을 막는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	/**
	 * 해당 회원의 아직 폐기되지 않은 모든 Refresh Token을 일괄 폐기한다.
	 * (비밀번호 변경, 재사용 탐지 시 전체 로그아웃 용도)
	 */
	@Modifying(clearAutomatically = true)
	@Query("update RefreshToken rt set rt.revokedAt = :now "
			+ "where rt.memberId = :memberId and rt.revokedAt is null")
	int revokeAllByMemberId(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);

	/** 만료된 지 오래된 토큰을 정리한다(BE-107) - 폐기 여부와 무관하게 {@code cutoff} 이전에 만료된 행 전부. */
	@Modifying(clearAutomatically = true)
	@Query("delete from RefreshToken rt where rt.expiresAt < :cutoff")
	int deleteByExpiresAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
