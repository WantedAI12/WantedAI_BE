package com.perfumeryaicore.domain.project.dto.request;

import com.perfumeryaicore.global.common.ProjectRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 이메일로 기존 회원을 프로젝트에 초대하고 역할을 배정한다.
 */
public record AddProjectMemberRequest(

		@NotBlank
		@Email
		String email,

		@NotNull
		ProjectRole role
) {
}
