package com.perfumeryaicore.domain.member.dto.response;

import com.perfumeryaicore.domain.member.entity.Member;
import java.util.List;

/**
 * 내 프로필 응답. {@code projects}는 project 도메인 구현 시 채워진다.
 */
public record MemberResponse(
		Long memberId,
		String email,
		String name,
		List<ProjectRole> projects
) {

	public record ProjectRole(Long projectId, String role) {
	}

	public static MemberResponse from(Member member, List<ProjectRole> projects) {
		return new MemberResponse(member.getId(), member.getEmail(), member.getName(), projects);
	}

	public static MemberResponse from(Member member) {
		return from(member, List.of());
	}
}
