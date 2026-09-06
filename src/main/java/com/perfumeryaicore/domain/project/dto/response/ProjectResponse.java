package com.perfumeryaicore.domain.project.dto.response;

import com.perfumeryaicore.domain.project.entity.Project;
import com.perfumeryaicore.global.common.ProjectRole;
import java.time.LocalDateTime;

/**
 * 프로젝트 요약/상세 응답. {@code myRole}은 요청자의 이 프로젝트 내 역할이다.
 */
public record ProjectResponse(
		Long projectId,
		String name,
		String description,
		ProjectRole myRole,
		long memberCount,
		LocalDateTime createdAt
) {

	public static ProjectResponse of(Project project, ProjectRole myRole, long memberCount) {
		return new ProjectResponse(
				project.getId(),
				project.getName(),
				project.getDescription(),
				myRole,
				memberCount,
				project.getCreatedAt());
	}
}
