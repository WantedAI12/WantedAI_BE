package com.perfumeryaicore.domain.member.repository;

import com.perfumeryaicore.domain.member.entity.RefreshToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/**
	 * 해당 회원의 아직 폐기되지 않은 모든 Refresh Token을 일괄 폐기한다.
	 * (비밀번호 변경, 재사용 탐지 시 전체 로그아웃 용도)
	 */
	@Modifying(clearAutomatically = true)
	@Query("update RefreshToken rt set rt.revokedAt = :now "
			+ "where rt.memberId = :memberId and rt.revokedAt is null")
	int revokeAllByMemberId(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
