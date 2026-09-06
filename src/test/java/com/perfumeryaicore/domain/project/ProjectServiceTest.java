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
	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final ProjectService service = new ProjectService(
			projectRepository, projectMemberRepository, memberRepository,
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

		ProjectResponse res = service.create(ACTOR_ID, new CreateProjectRequest("여름 프로젝트", "설명"));

		assertThat(res.myRole()).isEqualTo(ProjectRole.ORG_ADMIN);
		assertThat(res.memberCount()).isEqualTo(1);

		ArgumentCaptor<ProjectMember> captor = ArgumentCaptor.forClass(ProjectMember.class);
		verify(projectMemberRepository).save(captor.capture());
		assertThat(captor.getValue().getRole()).isEqualTo(ProjectRole.ORG_ADMIN);
		assertThat(captor.getValue().getMemberId()).isEqualTo(ACTOR_ID);
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

		assertThatThrownBy(() -> service.update(PROJECT_ID, ACTOR_ID, new UpdateProjectRequest("새 이름", null)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
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
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.ORG_ADMIN)));
		when(projectMemberRepository.countByProjectIdAndRole(PROJECT_ID, ProjectRole.ORG_ADMIN)).thenReturn(1L);

		assertThatThrownBy(() -> service.changeMemberRole(PROJECT_ID, ACTOR_ID, TARGET_ID,
				new ChangeProjectMemberRoleRequest(ProjectRole.PERFUMER)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_LAST_ADMIN);
	}

	@Test
	void remove_member_blocks_removing_the_last_org_admin() {
		actorHasRole(ProjectRole.ORG_ADMIN);
		when(projectMemberRepository.findByProjectIdAndMemberId(PROJECT_ID, TARGET_ID))
				.thenReturn(Optional.of(ProjectMember.create(PROJECT_ID, TARGET_ID, ProjectRole.ORG_ADMIN)));
		when(projectMemberRepository.countByProjectIdAndRole(PROJECT_ID, ProjectRole.ORG_ADMIN)).thenReturn(1L);

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
}
