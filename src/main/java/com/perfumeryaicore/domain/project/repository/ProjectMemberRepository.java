package com.perfumeryaicore.domain.project.repository;

import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.global.common.ProjectRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

	Optional<ProjectMember> findByProjectIdAndMemberId(Long projectId, Long memberId);

	boolean existsByProjectIdAndMemberId(Long projectId, Long memberId);

	List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(Long projectId);

	List<ProjectMember> findByMemberIdOrderByCreatedAtDesc(Long memberId);

	long countByProjectId(Long projectId);

	long countByProjectIdAndRole(Long projectId, ProjectRole role);
}
