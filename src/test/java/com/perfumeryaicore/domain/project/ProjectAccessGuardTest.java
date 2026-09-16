package com.perfumeryaicore.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.domain.project.repository.ProjectMemberRepository;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProjectAccessGuardTest {

	private final ProjectMemberRepository repository = mock(ProjectMemberRepository.class);
	private final ProjectAccessGuard guard = new ProjectAccessGuard(repository);

	@Test
	void require_member_returns_the_role_when_the_user_belongs_to_the_project() {
		when(repository.findByProjectIdAndMemberId(10L, 1L))
				.thenReturn(Optional.of(ProjectMember.create(10L, 1L, ProjectRole.SAFETY_REVIEWER)));

		assertThat(guard.requireMember(10L, 1L)).isEqualTo(ProjectRole.SAFETY_REVIEWER);
	}

	@Test
	void require_member_denies_a_stranger() {
		when(repository.findByProjectIdAndMemberId(10L, 99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> guard.requireMember(10L, 99L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ACCESS_DENIED);
	}

	@Test
	void require_role_forbids_a_member_whose_role_is_not_allowed() {
		when(repository.findByProjectIdAndMemberId(10L, 1L))
				.thenReturn(Optional.of(ProjectMember.create(10L, 1L, ProjectRole.AUDITOR)));

		assertThatThrownBy(() -> guard.requireRole(10L, 1L, ProjectRole.ORG_ADMIN, ProjectRole.PROJECT_MANAGER))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	@Test
	void has_role_is_true_for_a_member_with_an_allowed_role() {
		when(repository.findByProjectIdAndMemberId(10L, 1L))
				.thenReturn(Optional.of(ProjectMember.create(10L, 1L, ProjectRole.SENSORY_SCIENTIST)));

		assertThat(guard.hasRole(10L, 1L, ProjectRole.SENSORY_SCIENTIST, ProjectRole.PROJECT_MANAGER)).isTrue();
	}

	@Test
	void has_role_is_false_without_throwing_for_a_member_with_a_different_role() {
		when(repository.findByProjectIdAndMemberId(10L, 1L))
				.thenReturn(Optional.of(ProjectMember.create(10L, 1L, ProjectRole.PERFUMER)));

		assertThat(guard.hasRole(10L, 1L, ProjectRole.SENSORY_SCIENTIST)).isFalse();
	}

	@Test
	void has_role_is_false_without_throwing_for_a_stranger() {
		when(repository.findByProjectIdAndMemberId(10L, 99L)).thenReturn(Optional.empty());

		assertThat(guard.hasRole(10L, 99L, ProjectRole.SENSORY_SCIENTIST)).isFalse();
	}

	@Test
	void require_write_role_allows_a_solo_org_admin_with_no_teammates_yet() {
		when(repository.findByProjectIdAndMemberId(10L, 1L))
				.thenReturn(Optional.of(ProjectMember.create(10L, 1L, ProjectRole.ORG_ADMIN)));
		when(repository.countByProjectId(10L)).thenReturn(1L);

		assertThat(guard.requireWriteRole(10L, 1L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND))
				.isEqualTo(ProjectRole.ORG_ADMIN);
	}

	/** 팀원이 초대돼 둘 이상이 되면 ORG_ADMIN도 실제 쓰기 역할이 있는 사람에게 맡겨야 한다. */
	@Test
	void require_write_role_denies_an_org_admin_once_other_members_exist() {
		when(repository.findByProjectIdAndMemberId(10L, 1L))
				.thenReturn(Optional.of(ProjectMember.create(10L, 1L, ProjectRole.ORG_ADMIN)));
		when(repository.countByProjectId(10L)).thenReturn(2L);

		assertThatThrownBy(() -> guard.requireWriteRole(10L, 1L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	@Test
	void require_write_role_passes_through_for_an_actual_write_role_regardless_of_team_size() {
		when(repository.findByProjectIdAndMemberId(10L, 1L))
				.thenReturn(Optional.of(ProjectMember.create(10L, 1L, ProjectRole.PERFUMER)));

		assertThat(guard.requireWriteRole(10L, 1L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND))
				.isEqualTo(ProjectRole.PERFUMER);
	}
}
