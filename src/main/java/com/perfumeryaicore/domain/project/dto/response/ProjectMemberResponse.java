package com.perfumeryaicore.domain.project.dto.response;

import com.perfumeryaicore.domain.member.entity.Member;
import com.perfumeryaicore.domain.project.entity.ProjectMember;
import com.perfumeryaicore.global.common.ProjectRole;
import java.time.LocalDateTime;

public record ProjectMemberResponse(
		Long memberId,
		String email,
		String name,
		ProjectRole role,
		LocalDateTime joinedAt
) {

	public static ProjectMemberResponse of(ProjectMember membership, Member member) {
		return new ProjectMemberResponse(
				member.getId(),
				member.getEmail(),
				member.getName(),
				membership.getRole(),
				membership.getCreatedAt());
	}
}
