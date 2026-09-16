package com.perfumeryaicore.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.member.repository.MemberRepository;
import com.perfumeryaicore.domain.project.dto.request.AddProjectMemberRequest;
import com.perfumeryaicore.domain.project.dto.request.ChangeProjectMemberRoleRequest;
import com.perfumeryaicore.domain.project.dto.request.CreateProjectRequest;
import com.perfumeryaicore.domain.project.dto.request.UpdateProjectRequest;
import com.perfumeryaicore.domain.project.dto.response.ProjectResponse;
import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.entity.ProjectMemberAction;
import com.perfumeryaicore.domain.project.repository.ProjectMemberAuditLogRepository;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.repository.ProjectRepository;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.project.service.ProjectService;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProjectServiceTest {

	private static final long PROJECT_ID = 10L;
	private static final long ACTOR_ID = 1L;
	private static final long TARGET_ID = 2L;

	private final ProjectRepository projectRepository = mock(ProjectRepository.class);
	private final ProjectMemberRepository projectMemberRepository = mock(ProjectMemberRepository.class);
	private final ProjectMemberAuditLogRepository auditLogRepository = mock(ProjectMemberAuditLogRepository.class);
	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final ProjectService service = new ProjectService(
			projectRepository, projectMemberRepository, auditLogRepository, memberRepository,
			new ProjectAccessGuard(projectMemberRepository));

	private void actorHasRole(ProjectRole role) {
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, ACTOR_ID))
				.thenReturn(Optional.of(ProjectMember.create(PROJECT_ID, ACTOR_ID, role)));
	}

	private Member member(long id, String email) {
		Member m = Member.builder().email(email).passwordHash("x").name("이름" + id).build();
		try {
			var field = Member.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(m, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return m;
	}

	@Test
	void create_registers_the_creator_as_org_admin() {
		when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
		when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

		ProjectResponse res = service.create(ACTOR_ID, new CreateProjectRequest("여름 프로젝트", "설명", null, null));

		assertThat(res.myRole()).isEqualTo(ProjectRole.ORG_ADMIN);
		assertThat(res.memberCount()).isEqualTo(1);

		ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);
		verify(projectMemberRepository).save(captor.capture());
		assertThat(captor.getValue().getRole()).isEqualTo(ProjectRole.ORG_ADMIN);
		assertThat(captor.getValue().getMemberId()).isEqualTo(ACTOR_ID);
	}

	/**
	 * 게스트 모드: ORG_ADMIN은 요청 작성 쓰기 역할(WRITE_ROLES)이 아니라서, 게스트가 ORG_ADMIN으로
	 * 시작하면 초대할 팀원도 없이 자기 프로젝트에서 요청 하나 못 만드는 상태가 된다 - PERFUMER로
	 * 시작해야 한다.
	 */
	@Test
	void create_registers_a_guest_creator_as_perfumer_instead_of_org_admin() {
		when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
		when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));
		Member guest = Member.createGuest(
				"guest+z@guest.perfumery.local", "hash", java.time.LocalDateTime.now().plusHours(24));
		when(memberRepository.findById(ACTOR_ID)).thenReturn(Optional.of(guest));

		ProjectResponse res = service.create(ACTOR_ID, new CreateProjectRequest("체험 프로젝트", null, null, null));

		assertThat(res.myRole()).isEqualTo(ProjectRole.PERFUMER);
	}

	@Test
	void get_denies_a_non_member() {
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, ACTOR_ID))
				.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(PROJECT_ID, ACTOR_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
	}

	@Test
	void update_is_forbidden_for_a_plain_member_role() {
		actorHasRole(ProjectRole.PERFUMER);

		assertThatThrownBy(() -> service.update(PROJECT_ID, ACTOR_ID, new UpdateProjectRequest("새 이름", null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	/** BE-084: PATCH로 프로젝트 이름을 공백으로 바꿀 수 없다. */
	@Test
	void update_rejects_a_blank_name() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		when(projectRepository.findById(PROJECT_ID))
				.thenReturn(Optional.of(Project.create("기존 이름", "설명", null, null)));

		assertThatThrownBy(() -> service.update(PROJECT_ID, ACTOR_ID, new UpdateProjectRequest("   ", null, null, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
	}

	@Test
	void create_rejects_a_due_date_before_the_start_date() {
		assertThatThrownBy(() -> service.create(ACTOR_ID, new CreateProjectRequest(
				"프로젝트", null, java.time.LocalDate.of(2026, 6, 10), java.time.LocalDate.of(2026, 6, 1))))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
		verify(projectRepository, never()).save(any());
	}

	@Test
	void update_rejects_an_assignee_who_is_not_a_project_member() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		when(projectRepository.findById(PROJECT_ID))
				.thenReturn(Optional.of(Project.create("기존 이름", "설명", null, null)));
		when(projectMemberRepository.existsByProjectIdAndMemberId(PROJECT_ID, TARGET_ID)).thenReturn(false);

		assertThatThrownBy(() -> service.update(PROJECT_ID, ACTOR_ID,
				new UpdateProjectRequest(null, null, null, null, TARGET_ID)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_MEMBER_NOT_FOUND);
	}

	@Test
	void update_applies_schedule_and_assignee_when_the_assignee_is_a_member() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		when(projectRepository.findById(PROJECT_ID))
				.thenReturn(Optional.of(Project.create("기존 이름", "설명", null, null)));
		when(projectMemberRepository.existsByProjectIdAndMemberId(PROJECT_ID, TARGET_ID)).thenReturn(true);
		java.time.LocalDate start = java.time.LocalDate.of(2026, 6, 1);
		java.time.LocalDate due = java.time.LocalDate.of(2026, 6, 30);

		ProjectResponse res = service.update(PROJECT_ID, ACTOR_ID,
				new UpdateProjectRequest(null, null, start, due, TARGET_ID));

		assertThat(res.startDate()).isEqualTo(start);
		assertThat(res.dueDate()).isEqualTo(due);
		assertThat(res.assigneeMemberId()).isEqualTo(TARGET_ID);
	}

	@Test
	void add_member_rejects_an_unknown_email() {
		actorHasRole(ProjectRole.PROJECT_MANAGER);
		when(memberRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.addMember(PROJECT_ID, ACTOR_ID,
				new AddProjectMemberRequest("ghost@example.com", ProjectRole.PERFUMER)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
	}

	@Test
	void add_member_rejects_someone_already_in_the_project() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		when(memberRepository.findByEmail("dup@example.com"))
				.thenReturn(Optional.of(member(TARGET_ID, "dup@example.com")));
		when(projectMemberRepository.existsByProjectIdAndMemberId(PROJECT_ID, TARGET_ID)).thenReturn(true);

		assertThatThrownBy(() -> service.addMember(PROJECT_ID, ACTOR_ID,
				new AddProjectMemberRequest("dup@example.com", ProjectRole.PERFUMER)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
	}

	@Test
	void change_role_blocks_demoting_the_last_org_admin() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		ProjectMember target = ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.ORG_ADMIN);
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(target));
		when(projectMemberRepository.findByProjectIdAndRoleForUpdate(PROJECT_ID, ProjectRole.ORG_ADMIN))
				.thenReturn(java.util.List.of(target));

		assertThatThrownBy(() -> service.changeMemberRole(PROJECT_ID, ACTOR_ID, TARGET_ID,
				new ChangeProjectMemberRoleRequest(ProjectRole.PERFUMER)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_LAST_ADMIN);
	}

	@Test
	void remove_member_blocks_removing_the_last_org_admin() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		ProjectMember target = ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.ORG_ADMIN);
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(target));
		when(projectMemberRepository.findByProjectIdAndRoleForUpdate(PROJECT_ID, ProjectRole.ORG_ADMIN))
				.thenReturn(java.util.List.of(target));

		assertThatThrownBy(() -> service.removeMember(PROJECT_ID, ACTOR_ID, TARGET_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_LAST_ADMIN);
		verify(projectMemberRepository, never()).delete(any());
	}

	@Test
	void remove_member_succeeds_for_a_non_admin() {
		actorHasRole(ProjectRole.PROJECT_MANAGER);
		ProjectMember target = ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.PERFUMER);
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(target));

		service.removeMember(PROJECT_ID, ACTOR_ID, TARGET_ID);

		verify(projectMemberRepository).delete(target);
	}

	/** BE-008: PROJECT_MANAGER는 ORG_ADMIN 역할을 부여할 수 없다. */
	@Test
	void add_member_as_org_admin_is_forbidden_for_a_project_manager() {
		actorHasRole(ProjectRole.PROJECT_MANAGER);

		assertThatThrownBy(() -> service.addMember(PROJECT_ID, ACTOR_ID,
				new AddProjectMemberRequest("new@example.com", ProjectRole.ORG_ADMIN)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(memberRepository, never()).findByEmail(any());
	}

	@Test
	void add_member_as_org_admin_succeeds_for_an_org_admin() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		when(memberRepository.findByEmail("new@example.com"))
				.thenReturn(Optional.of(member(TARGET_ID, "new@example.com")));
		when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

		var response = service.addMember(PROJECT_ID, ACTOR_ID,
				new AddProjectMemberRequest("new@example.com", ProjectRole.ORG_ADMIN));

		assertThat(response.role()).isEqualTo(ProjectRole.ORG_ADMIN);
	}

	/** BE-008: PROJECT_MANAGER는 다른 멤버를 ORG_ADMIN으로 승격시킬 수 없다. */
	@Test
	void change_role_to_org_admin_is_forbidden_for_a_project_manager() {
		actorHasRole(ProjectRole.PROJECT_MANAGER);
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.PERFUMER)));

		assertThatThrownBy(() -> service.changeMemberRole(PROJECT_ID, ACTOR_ID, TARGET_ID,
				new ChangeProjectMemberRoleRequest(ProjectRole.ORG_ADMIN)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	/** BE-008: PROJECT_MANAGER는 기존 ORG_ADMIN을 강등(회수)시킬 수도 없다 — 마지막 관리자가 아니어도. */
	@Test
	void change_role_away_from_org_admin_is_forbidden_for_a_project_manager() {
		actorHasRole(ProjectRole.PROJECT_MANAGER);
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.ORG_ADMIN)));
		when(projectMemberRepository.countByProjectIdAndRole(PROJECT_ID, ProjectRole.ORG_ADMIN)).thenReturn(2L);

		assertThatThrownBy(() -> service.changeMemberRole(PROJECT_ID, ACTOR_ID, TARGET_ID,
				new ChangeProjectMemberRoleRequest(ProjectRole.PERFUMER)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	/** BE-009: 관리자가 둘 이상 남아있으면 강등이 통과하고, 이때 잠금 조회가 실제로 쓰인다. */
	@Test
	void change_role_away_from_org_admin_succeeds_when_another_admin_remains() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		ProjectMember target = ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.ORG_ADMIN);
		ProjectMember other = ProjectMember.create(PROJECT_ID, 3L, ProjectRole.ORG_ADMIN);
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(target));
		when(projectMemberRepository.findByProjectIdAndRoleForUpdate(PROJECT_ID, ProjectRole.ORG_ADMIN))
				.thenReturn(java.util.List.of(target, other));
		when(memberRepository.findById(TARGET_ID)).thenReturn(Optional.of(member(TARGET_ID, "t@example.com")));

		service.changeMemberRole(PROJECT_ID, ACTOR_ID, TARGET_ID,
				new ChangeProjectMemberRoleRequest(ProjectRole.PERFUMER));

		assertThat(target.getRole()).isEqualTo(ProjectRole.PERFUMER);
		verify(projectMemberRepository).findByProjectIdAndRoleForUpdate(PROJECT_ID, ProjectRole.ORG_ADMIN);
	}

	/** BE-008: PROJECT_MANAGER는 ORG_ADMIN을 제거할 수도 없다 — 마지막 관리자가 아니어도. */
	@Test
	void remove_member_of_an_org_admin_is_forbidden_for_a_project_manager() {
		actorHasRole(ProjectRole.PROJECT_MANAGER);
		ProjectMember target = ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.ORG_ADMIN);
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(target));
		when(projectMemberRepository.countByProjectIdAndRole(PROJECT_ID, ProjectRole.ORG_ADMIN)).thenReturn(2L);

		assertThatThrownBy(() -> service.removeMember(PROJECT_ID, ACTOR_ID, TARGET_ID))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(projectMemberRepository, never()).delete(any());
	}

	/** BE-010: 멤버 추가는 ADDED 감사 로그를 남긴다. */
	@Test
	void add_member_records_an_added_audit_log() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		when(memberRepository.findByEmail("new@example.com"))
				.thenReturn(Optional.of(member(TARGET_ID, "new@example.com")));
		when(projectMemberRepository.save(any(ProjectMember.class))).thenAnswer(inv -> inv.getArgument(0));

		service.addMember(PROJECT_ID, ACTOR_ID, new AddProjectMemberRequest("new@example.com", ProjectRole.PERFUMER));

		var captor = ArgumentCaptor.forClass(com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		assertThat(captor.getValue().getAction()).isEqualTo(ProjectMemberAction.ADDED);
		assertThat(captor.getValue().getTargetMemberId()).isEqualTo(TARGET_ID);
		assertThat(captor.getValue().getActorId()).isEqualTo(ACTOR_ID);
		assertThat(captor.getValue().getNewRole()).isEqualTo(ProjectRole.PERFUMER);
	}

	/** BE-010: 역할 변경은 이전·이후 역할을 함께 담은 ROLE_CHANGED 감사 로그를 남긴다. */
	@Test
	void change_role_records_a_role_changed_audit_log() {
		actorHasRole(ProjectRole.PROJECT_MANAGER);
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.PERFUMER)));
		when(memberRepository.findById(TARGET_ID)).thenReturn(Optional.of(member(TARGET_ID, "t@example.com")));

		service.changeMemberRole(PROJECT_ID, ACTOR_ID, TARGET_ID,
				new ChangeProjectMemberRoleRequest(ProjectRole.SAFETY_REVIEWER));

		var captor = ArgumentCaptor.forClass(com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		assertThat(captor.getValue().getAction()).isEqualTo(ProjectMemberAction.ROLE_CHANGED);
		assertThat(captor.getValue().getPreviousRole()).isEqualTo(ProjectRole.PERFUMER);
		assertThat(captor.getValue().getNewRole()).isEqualTo(ProjectRole.SAFETY_REVIEWER);
	}

	/** BE-010: 멤버 제거는 제거 당시 역할을 담은 REMOVED 감사 로그를 남긴다. */
	@Test
	void remove_member_records_a_removed_audit_log() {
		actorHasRole(ProjectRole.PROJECT_MANAGER);
		ProjectMember target = ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.PERFUMER);
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(target));

		service.removeMember(PROJECT_ID, ACTOR_ID, TARGET_ID);

		var captor = ArgumentCaptor.forClass(com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog.class);
		verify(auditLogRepository).save(captor.capture());
		assertThat(captor.getValue().getAction()).isEqualTo(ProjectMemberAction.REMOVED);
		assertThat(captor.getValue().getPreviousRole()).isEqualTo(ProjectRole.PERFUMER);
	}

	/** BE-010: 감사 이력 조회는 ORG_ADMIN/PROJECT_MANAGER가 아니면 거부된다. */
	@Test
	void member_audit_log_is_forbidden_for_a_plain_member() {
		actorHasRole(ProjectRole.PERFUMER);

		assertThatThrownBy(() -> service.memberAuditLog(PROJECT_ID, ACTOR_ID, org.springframework.data.domain.PageRequest.of(0, 20)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	@Test
	void member_audit_log_returns_the_repository_history_for_an_org_admin() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		var entry = com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog.added(
				PROJECT_ID, TARGET_ID, ACTOR_ID, ProjectRole.PERFUMER);
		var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
		when(auditLogRepository.findByProjectIdOrderByCreatedAtDesc(PROJECT_ID, pageable))
				.thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(entry)));

		var result = service.memberAuditLog(PROJECT_ID, ACTOR_ID, pageable);

		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0).action()).isEqualTo(ProjectMemberAction.ADDED);
	}
}
