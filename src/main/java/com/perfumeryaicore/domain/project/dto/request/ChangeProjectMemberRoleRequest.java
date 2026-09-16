package com.perfumeryaicore.domain.project.dto.request;

import com.perfumeryaicore.global.common.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record ChangeProjectMemberRoleRequest(

		@NotNull
		ProjectRole role
) {
}
