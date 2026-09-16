package com.perfumeryaicore.domain.project.repository;

import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.global.common.ProjectRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

	Optional<ProjectMember> findByProjectIdAndMemberId(Long projectId, Long memberId);

	boolean existsByProjectIdAndMemberId(Long projectId, Long memberId);

	List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(Long projectId);

	List<ProjectMember> findByMemberIdOrderByCreatedAtDesc(Long memberId);

	long countByProjectId(Long projectId);

	long countByProjectIdAndRole(Long projectId, ProjectRole role);

	/**
	 * 마지막 ORG_ADMIN 보호를 위해 해당 프로젝트의 ORG_ADMIN 행을 잠그고 조회한다(BE-009).
	 *
	 * <p>강등·제거 요청이 "현재 관리자 수"를 확인하고 실제로 반영하기까지의 구간을 통째로 직렬화해서,
	 * 두 요청이 동시에 "아직 2명 남았다"고 읽고 둘 다 통과되는 경쟁 조건을 막는다. 같은 트랜잭션 안에서
	 * 이 조회를 먼저 호출한 뒤 강등/삭제를 수행해야 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select pm from ProjectMember pm where pm.projectId = :projectId and pm.role = :role")
	List<ProjectMember> findByProjectIdAndRoleForUpdate(@Param("projectId") Long projectId, @Param("role") ProjectRole role);
}
