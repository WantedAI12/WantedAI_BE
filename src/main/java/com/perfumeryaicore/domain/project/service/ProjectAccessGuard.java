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

	/**
	 * 요청·후보 작성 같은 실무 쓰기 작업 전용 검사({@code requireRole}과 같지만 한 가지 예외를
	 * 더 허용한다): 프로젝트에 멤버가 자신 하나뿐이면 {@code ORG_ADMIN}도 통과시킨다.
	 *
	 * <p>초대할 팀원이 아직 없는 1인 프로젝트에서는 생성자(자동으로 {@code ORG_ADMIN})가
	 * {@code PERFUMER} 등 실무 역할이 아니라는 이유만으로 자기 프로젝트에 요청 하나 못 쓰는
	 * 상태가 된다 - 그렇다고 자신을 다른 역할로 바꿀 수도 없다(마지막 관리자는 강등 불가,
	 * {@link ProjectService#isLastAdminLocked}). 멤버가 둘 이상이 되면 이 예외는 더 이상
	 * 적용되지 않는다 - 그때는 실제로 쓰기 역할을 가진 팀원에게 맡기라는 뜻이다.
	 */
	public ProjectRole requireWriteRole(Long projectId, Long memberId, ProjectRole... writeRoles) {
		ProjectRole role = requireMember(projectId, memberId);
		for (ProjectRole candidate : writeRoles) {
			if (role == candidate) {
				return role;
			}
		}
		if (role == ProjectRole.ORG_ADMIN && projectMemberRepository.countByProjectId(projectId) == 1) {
			return role;
		}
		throw new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	/**
	 * 프로젝트 기본 정보 수정 같은 관리 작업 전용 검사({@code requireRole}과 같지만 한 가지 예외를
	 * 더 허용한다): 프로젝트에 멤버가 자신 하나뿐이면 역할과 무관하게 통과시킨다.
	 *
	 * <p>게스트가 만든 1인 프로젝트의 생성자는 {@code ORG_ADMIN}이 아니라 {@code PERFUMER}로
	 * 등록된다({@link ProjectService#initialRoleFor} - 실무 쓰기 작업을 막지 않기 위함). 그 결과
	 * {@code requireRole(ORG_ADMIN, PROJECT_MANAGER)}만 쓰면 자기 프로젝트 이름 하나 못 바꾸는
	 * 상태가 된다 - 팀원이 없어 다른 사람을 해칠 여지가 없는 1인 프로젝트에서는 그 유일한
	 * 멤버가 사실상 관리자다. 멤버가 둘 이상이 되면 이 예외는 더 이상 적용되지 않는다.
	 */
	public ProjectRole requireManageRole(Long projectId, Long memberId, ProjectRole... manageRoles) {
		ProjectRole role = requireMember(projectId, memberId);
		for (ProjectRole candidate : manageRoles) {
			if (role == candidate) {
				return role;
			}
		}
		if (projectMemberRepository.countByProjectId(projectId) == 1) {
			return role;
		}
		throw new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	public boolean isMember(Long projectId, Long memberId) {
		return projectMemberRepository.existsByProjectIdAndMemberId(projectId, memberId);
	}

	/**
	 * 멤버이면서 {@code allowed} 중 하나의 역할인지 예외 없이 확인한다. {@link #requireRole}과 달리
	 * "역할에 따라 조회 범위를 조용히 좁힐지" 같은, 거부가 아니라 분기가 필요한 곳에서 쓴다.
	 * 멤버가 아니면 false.
	 */
	public boolean hasRole(Long projectId, Long memberId, ProjectRole... allowed) {
		return projectMemberRepository.findByProjectIdAndMemberId(projectId, memberId)
				.map(ProjectMember::getRole)
				.map(role -> {
					for (ProjectRole candidate : allowed) {
						if (role == candidate) {
							return true;
						}
					}
					return false;
				})
				.orElse(false);
	}
}
