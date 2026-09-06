package com.perfumeryaicore.domain.project.service;

import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.project.dto.request.AddProjectMemberRequest;
import com.perfumeryaicore.domain.project.dto.request.ChangeProjectMemberRoleRequest;
import com.perfumeryaicore.domain.project.dto.request.CreateProjectRequest;
import com.perfumeryaicore.domain.project.dto.request.UpdateProjectRequest;
import com.perfumeryaicore.domain.project.dto.response.ProjectMemberResponse;
import com.perfumeryaicore.domain.project.dto.response.ProjectResponse;
import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.repository.ProjectRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로젝트(테넌트) 생성·조회·수정과 멤버·역할 관리.
 *
 * <p>조직(Organization) 엔티티는 별도로 두지 않는다. 프로젝트를 생성한 사람이 자동으로
 * {@link ProjectRole#ORG_ADMIN}이 되고, 이후 멤버 초대/역할 변경은 {@code ORG_ADMIN} 또는
 * {@code PROJECT_MANAGER}만 할 수 있다. 마지막 {@code ORG_ADMIN}은 제거·강등할 수 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final ProjectMemberRepository projectMemberRepository;
	private final MemberRepository memberRepository;
	private final ProjectAccessGuard accessGuard;

	@Transactional
	public ProjectResponse create(Long memberId, CreateProjectRequest dto) {
		Project project = projectRepository.save(Project.create(dto.name(), dto.description()));
		projectMemberRepository.save(ProjectMember.create(project.getId(), memberId, ProjectRole.ORG_ADMIN));
		log.info("[PROJECT] id={} created by={} (ORG_ADMIN)", project.getId(), memberId);
		return ProjectResponse.of(project, ProjectRole.ORG_ADMIN, 1);
	}

	public List<ProjectResponse> listMine(Long memberId) {
		List<ProjectMember> memberships = projectMemberRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
		if (memberships.isEmpty()) {
			return List.of();
		}
		Map<Long, Project> projectById = projectRepository
				.findByIdInOrderByCreatedAtDesc(memberships.stream().map(ProjectMember::getProjectId).toList())
				.stream()
				.collect(Collectors.toMap(Project::getId, Function.identity()));

		return memberships.stream()
				.map(m -> {
					Project project = projectById.get(m.getProjectId());
					if (project == null) {
						return null;
					}
					return ProjectResponse.of(project, m.getRole(),
							projectMemberRepository.countByProjectId(project.getId()));
				})
				.filter(Objects::nonNull)
				.toList();
	}

	public ProjectResponse get(Long projectId, Long memberId) {
		ProjectRole myRole = accessGuard.requireMember(projectId, memberId);
		Project project = findProject(projectId);
		return ProjectResponse.of(project, myRole, projectMemberRepository.countByProjectId(projectId));
	}

	@Transactional
	public ProjectResponse update(Long projectId, Long memberId, UpdateProjectRequest dto) {
		ProjectRole myRole = accessGuard.requireRole(projectId, memberId,
				ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		Project project = findProject(projectId);
		project.updateInfo(dto.name(), dto.description());
		return ProjectResponse.of(project, myRole, projectMemberRepository.countByProjectId(projectId));
	}

	public List<ProjectMemberResponse> listMembers(Long projectId, Long memberId) {
		accessGuard.requireMember(projectId, memberId);
		List<ProjectMember> memberships = projectMemberRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
		Map<Long, Member> memberById = memberRepository
				.findAllById(memberships.stream().map(ProjectMember::getMemberId).toList())
				.stream()
				.collect(Collectors.toMap(Member::getId, Function.identity()));

		return memberships.stream()
				.map(pm -> {
					Member member = memberById.get(pm.getMemberId());
					if (member == null) {
						throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND);
					}
					return ProjectMemberResponse.of(pm, member);
				})
				.toList();
	}

	@Transactional
	public ProjectMemberResponse addMember(Long projectId, Long actorId, AddProjectMemberRequest dto) {
		accessGuard.requireRole(projectId, actorId, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		Member target = memberRepository.findByEmail(dto.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (projectMemberRepository.existsByProjectIdAndMemberId(projectId, target.getId())) {
			throw new BusinessException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
		}
		ProjectMember saved = projectMemberRepository.save(
				ProjectMember.create(projectId, target.getId(), dto.role()));
		log.info("[PROJECT] id={} member={} added as {} by={}",
				projectId, target.getId(), dto.role(), actorId);
		return ProjectMemberResponse.of(saved, target);
	}

	@Transactional
	public ProjectMemberResponse changeMemberRole(Long projectId, Long actorId, Long targetMemberId,
			ChangeProjectMemberRoleRequest dto) {
		accessGuard.requireRole(projectId, actorId, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		ProjectMember membership = projectMemberRepository.findByProjectIdAndMemberId(projectId, targetMemberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

		if (membership.isAdmin() && dto.role() != ProjectRole.ORG_ADMIN && isLastAdmin(projectId)) {
			throw new BusinessException(ErrorCode.PROJECT_LAST_ADMIN);
		}
		membership.changeRole(dto.role());

		Member target = memberRepository.findById(targetMemberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		log.info("[PROJECT] id={} member={} role -> {} by={}",
				projectId, targetMemberId, dto.role(), actorId);
		return ProjectMemberResponse.of(membership, target);
	}

	@Transactional
	public void removeMember(Long projectId, Long actorId, Long targetMemberId) {
		accessGuard.requireRole(projectId, actorId, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		ProjectMember membership = projectMemberRepository.findByProjectIdAndMemberId(projectId, targetMemberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

		if (membership.isAdmin() && isLastAdmin(projectId)) {
			throw new BusinessException(ErrorCode.PROJECT_LAST_ADMIN);
		}
		projectMemberRepository.delete(membership);
		log.info("[PROJECT] id={} member={} removed by={}", projectId, targetMemberId, actorId);
	}

	private boolean isLastAdmin(Long projectId) {
		return projectMemberRepository.countByProjectIdAndRole(projectId, ProjectRole.ORG_ADMIN) <= 1;
	}

	private Project findProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
	}
}
