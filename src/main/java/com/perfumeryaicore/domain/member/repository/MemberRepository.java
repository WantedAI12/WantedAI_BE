package com.perfumeryaicore.domain.member.repository;

import com.perfumeryaicore.domain.member.entity.Member;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByEmail(String email);

	boolean existsByEmail(String email);

	/** 게스트 데이터 정리 배치용 - 정리 로직 자체는 별도 배치에서 구현한다. */
	List<Member> findByGuestTrueAndGuestExpiresAtBefore(LocalDateTime cutoff);
}
