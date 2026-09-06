package com.perfumeryaicore.domain.project.dto.request;

import com.perfumeryaicore.domain.project.entity.Project;
import jakarta.validation.constraints.Size;

/**
 * 프로젝트 정보 부분 수정. {@code null}이 아닌 필드만 반영된다.
 */
public record UpdateProjectRequest(

		@Size(max = Project.NAME_MAX)
		String name,

		@Size(max = Project.DESCRIPTION_MAX)
		String description
) {
}
