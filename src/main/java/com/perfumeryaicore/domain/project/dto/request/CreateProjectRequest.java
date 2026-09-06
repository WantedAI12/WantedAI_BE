package com.perfumeryaicore.domain.project.dto.request;

import com.perfumeryaicore.domain.project.entity.Project;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(

		@NotBlank
		@Size(max = Project.NAME_MAX)
		String name,

		@Size(max = Project.DESCRIPTION_MAX)
		String description
) {
}
