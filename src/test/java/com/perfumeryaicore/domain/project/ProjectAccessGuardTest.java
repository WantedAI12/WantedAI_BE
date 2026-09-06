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
}
