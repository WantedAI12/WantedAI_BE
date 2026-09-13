package com.perfumeryaicore.domain.project.dto.response;

import com.perfumeryaicore.domain.project.entity.ProjectMemberAction;
import com.perfumeryaicore.domain.project.entity.ProjectMemberAuditLog;
import com.perfumeryaicore.global.common.ProjectRole;
import java.time.LocalDateTime;

public record ProjectMemberAuditLogResponse(
		Long targetMemberId,
		Long actorId,
		ProjectMemberAction action,
		ProjectRole previousRole,
		ProjectRole newRole,
		LocalDateTime occurredAt
) {

	public static ProjectMemberAuditLogResponse from(ProjectMemberAuditLog log) {
		return new ProjectMemberAuditLogResponse(
				log.getTargetMemberId(), log.getActorId(), log.getAction(),
				log.getPreviousRole(), log.getNewRole(), log.getCreatedAt());
	}
}
