package com.perfumeryaicore.domain.project.service;

import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 테넌트(프로젝트) 격리와 역할 검증의 단일 진입점.
 *
 * <p>다른 도메인 서비스가 "이 사용자가 이 프로젝트의 멤버인가 / 특정 역할인가"를 확인할 때 주입해서 쓴다.
 * (현재는 project 도메인 자체에서만 사용하며, 나머지 도메인은 후속 작업에서 이 가드로 교체한다.)
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectAccessGuard {

	private final ProjectMemberRepository projectMemberRepository;

	/** 프로젝트 멤버가 아니면 {@code PROJECT_ACCESS_DENIED}. 멤버면 그 역할을 반환한다. */
	public ProjectRole requireMember(Long projectId, Long memberId) {
		return projectMemberRepository.findByProjectIdAndMemberId(projectId, memberId)
				.map(ProjectMember::getRole)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED));
	}

	/**
	 * 멤버이면서 {@code allowed} 중 하나의 역할이어야 한다.
	 * 멤버가 아니면 {@code PROJECT_ACCESS_DENIED}, 역할이 부족하면 {@code PROJECT_ROLE_FORBIDDEN}.
	 */
	public ProjectRole requireRole(Long projectId, Long memberId, ProjectRole... allowed) {
		ProjectRole role = requireMember(projectId, memberId);
		for (ProjectRole candidate : allowed) {
			if (role == candidate) {
				return role;
			}
		}
		throw new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	public boolean isMember(Long projectId, Long memberId) {
		return projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId);
	}
}
