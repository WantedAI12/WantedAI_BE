package com.perfumeryaicore.domain.project.service;

import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.project.dto.request.AddProjectMemberRequest;
import com.perfumeryaicore.domain.project.dto.request.ChangeProjectMemberRoleRequest;
import com.perfumeryaicore.domain.project.dto.request.CreateProjectRequest;
import com.perfumeryaicore.domain.project.dto.request.UpdateProjectRequest;
import com.perfumeryaicore.domain.project.dto.response.ProjectMemberAuditLogResponse;
import com.perfumeryaicore.domain.project.dto.response.ProjectMemberResponse;
import com.perfumeryaicore.domain.project.dto.response.ProjectResponse;
import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog;
import com.perfumeryaicore.domain.project.repository.ProjectMemberAuditLogRepository;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.repository.ProjectRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import com.perfumeryaicore.global.response.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
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
	private final ProjectMemberAuditLogRepository auditLogRepository;
	private final MemberRepository memberRepository;
	private final ProjectAccessGuard accessGuard;

	@Transactional
	public ProjectResponse create(Long memberId, CreateProjectRequest dto) {
		Project project = projectRepository.save(
				Project.create(dto.name(), dto.description(), dto.startDate(), dto.dueDate()));
		ProjectRole initialRole = initialRoleFor(memberId);
		projectMemberRepository.save(ProjectMember.create(project.getId(), memberId, initialRole));
		log.info("[PROJECT] id={} created by={} ({})", project.getId(), memberId, initialRole);
		return ProjectResponse.of(project, initialRole, 1);
	}

	/**
	 * 게스트 모드: 게스트는 초대할 팀원이 없어 {@code ORG_ADMIN}의 관리 권한(멤버 초대·역할 변경)이
	 * 의미가 없고, 오히려 {@code ORG_ADMIN}은 요청 작성 쓰기 역할({@code WRITE_ROLES})이 아니라서
	 * 자기 프로젝트에서 요청 하나 업데이트·확정도 못 하는 문제가 생긴다 - 게스트가 만드는 프로젝트는
	 * 대신 {@code PERFUMER}로 시작해 체험 흐름 전체(요청 작성부터 후보 생성까지)가 막히지 않게 한다.
	 */
	private ProjectRole initialRoleFor(Long memberId) {
		return memberRepository.findById(memberId).map(Member::isGuest).orElse(false)
				? ProjectRole.PERFUMER
				: ProjectRole.ORG_ADMIN;
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
		ProjectRole myRole = accessGuard.requireManageRole(projectId, memberId,
				ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		Project project = findProject(projectId);
		project.updateInfo(dto.name(), dto.description());
		if (dto.assigneeMemberId() != null
				&& !projectMemberRepository.existsByProjectIdAndMemberId(projectId, dto.assigneeMemberId())) {
			throw new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND, "담당자는 이 프로젝트의 멤버여야 합니다.");
		}
		project.updateSchedule(dto.startDate(), dto.dueDate(), dto.assigneeMemberId());
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
		// requireManageRole: 게스트가 만든 1인 프로젝트의 생성자(PERFUMER)도 팀원을 초대할 수
		// 있어야 한다 - 안 그러면 초대해서 벗어날 방법도 없이 혼자 갇힌다(update()와 같은 이유,
		// 운영 리포트 후속 확인, 2026-09-18). ORG_ADMIN 역할 부여는 아래 requireOrgAdminActor가
		// 별도로 계속 막는다 - 이 완화는 "초대 자체"만 풀어준다.
		ProjectRole actorRole = accessGuard.requireManageRole(projectId, actorId,
				ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		if (dto.role() == ProjectRole.ORG_ADMIN) {
			requireOrgAdminActor(actorRole, "ORG_ADMIN 역할 부여");
		}
		Member target = memberRepository.findByEmail(dto.email())
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (projectMemberRepository.existsByProjectIdAndMemberId(projectId, target.getId())) {
			throw new BusinessException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
		}
		ProjectMember saved = projectMemberRepository.save(
				ProjectMember.create(projectId, target.getId(), dto.role()));
		auditLogRepository.save(ProjectMemberAuditLog.added(projectId, target.getId(), actorId, dto.role()));
		log.info("[PROJECT] id={} member={} added as {} by={}",
				projectId, target.getId(), dto.role(), actorId);
		return ProjectMemberResponse.of(saved, target);
	}

	@Transactional
	public ProjectMemberResponse changeMemberRole(Long projectId, Long actorId, Long targetMemberId,
			ChangeProjectMemberRoleRequest dto) {
		ProjectRole actorRole = accessGuard.requireRole(projectId, actorId,
				ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		ProjectMember membership = projectMemberRepository.findByProjectIdAndMemberId(projectId, targetMemberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

		// 대상이 이미 ORG_ADMIN이면(회수) 또는 새 역할이 ORG_ADMIN이면(부여) 행위자도 ORG_ADMIN이어야 한다.
		if (membership.isAdmin() || dto.role() == ProjectRole.ORG_ADMIN) {
			requireOrgAdminActor(actorRole, "ORG_ADMIN 역할 부여·회수");
		}
		if (membership.isAdmin() && dto.role() != ProjectRole.ORG_ADMIN && isLastAdminLocked(projectId)) {
			throw new BusinessException(ErrorCode.PROJECT_LAST_ADMIN);
		}
		ProjectRole previousRole = membership.getRole();
		membership.changeRole(dto.role());
		auditLogRepository.save(
				ProjectMemberAuditLog.roleChanged(projectId, targetMemberId, actorId, previousRole, dto.role()));

		Member target = memberRepository.findById(targetMemberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		log.info("[PROJECT] id={} member={} role -> {} by={}",
				projectId, targetMemberId, dto.role(), actorId);
		return ProjectMemberResponse.of(membership, target);
	}

	@Transactional
	public void removeMember(Long projectId, Long actorId, Long targetMemberId) {
		ProjectRole actorRole = accessGuard.requireRole(projectId, actorId,
				ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		ProjectMember membership = projectMemberRepository.findByProjectIdAndMemberId(projectId, targetMemberId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND));

		if (membership.isAdmin()) {
			requireOrgAdminActor(actorRole, "ORG_ADMIN 제거");
		}
		if (membership.isAdmin() && isLastAdminLocked(projectId)) {
			throw new BusinessException(ErrorCode.PROJECT_LAST_ADMIN);
		}
		auditLogRepository.save(
				ProjectMemberAuditLog.removed(projectId, targetMemberId, actorId, membership.getRole()));
		projectMemberRepository.delete(membership);
		log.info("[PROJECT] id={} member={} removed by={}", projectId, targetMemberId, actorId);
	}

	/**
	 * 멤버 추가·역할 변경·제거의 불변 감사 이력을 최신순으로 조회한다(ORG_ADMIN/PROJECT_MANAGER 전용, BE-010).
	 * 이력은 시간이 지날수록 무한정 쌓이므로 페이지네이션한다(BE-085).
	 */
	public PageResponse<ProjectMemberAuditLogResponse> memberAuditLog(Long projectId, Long memberId, Pageable pageable) {
		accessGuard.requireRole(projectId, memberId, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER);
		return PageResponse.of(auditLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId, pageable)
				.map(ProjectMemberAuditLogResponse::from));
	}

	/**
	 * ORG_ADMIN 역할의 생성·부여·회수는 조직 관리 권한(ORG_ADMIN)으로 한정한다(BE-008).
	 * PROJECT_MANAGER는 다른 역할은 자유롭게 배정할 수 있지만, ORG_ADMIN을 주거나 뺏을 수는 없다.
	 */
	private void requireOrgAdminActor(ProjectRole actorRole, String action) {
		if (actorRole != ProjectRole.ORG_ADMIN) {
			throw new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN, action + "은(는) 조직 관리자만 할 수 있습니다.");
		}
	}

	/**
	 * ORG_ADMIN 행을 잠그고(PESSIMISTIC_WRITE) 마지막 관리자인지 확인한다(BE-009).
	 *
	 * <p>단순 COUNT 조회는 동시에 들어온 두 개의 강등·제거 요청이 서로의 결과를 보지 못한 채
	 * 둘 다 "아직 2명 이상"이라고 판단해 통과시킬 수 있다. 이 메서드가 호출한 시점부터 트랜잭션이
	 * 끝날 때까지 해당 프로젝트의 ORG_ADMIN 행에 대한 잠금이 유지되므로, 같은 프로젝트를 대상으로
	 * 동시에 들어온 요청은 이 지점에서 직렬화되어 하나가 커밋된 뒤에야 다음 요청이 최신 개수를 읽는다.
	 */
	private boolean isLastAdminLocked(Long projectId) {
		return projectMemberRepository.findByProjectIdAndRoleForUpdate(projectId, ProjectRole.ORG_ADMIN).size() <= 1;
	}

	private Project findProject(Long projectId) {
		return projectRepository.findById(projectId)
				.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
	}
}
