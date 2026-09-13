package com.perfumeryaicore.domain.project.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import com.perfumeryaicore.global.common.ProjectRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 멤버 추가·역할 변경·제거 이력 한 건(불변, BE-010).
 *
 * <p>{@code experiment} 도메인의 {@code ExperimentStatusLog}와 같은 패턴: 상태 자체는
 * {@link ProjectMember}가 소유하고, 이 로그는 "누가 언제 누구를 어떤 역할로 바꿨는지"만
 * INSERT-ONLY로 남긴다 — update/delete 메서드를 두지 않는다.
 */
@Entity
@Getter
@Table(
		name = "project_member_audit_logs",
		indexes = @Index(name = "idx_project_member_audit_logs_project_id", columnList = "project_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMemberAuditLog extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "target_member_id", nullable = false)
	private Long targetMemberId;

	@Column(name = "actor_id", nullable = false)
	private Long actorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProjectMemberAction action;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_role", length = 20)
	private ProjectRole previousRole;

	@Enumerated(EnumType.STRING)
	@Column(name = "new_role", length = 20)
	private ProjectRole newRole;

	private ProjectMemberAuditLog(Long projectId, Long targetMemberId, Long actorId,
			ProjectMemberAction action, ProjectRole previousRole, ProjectRole newRole) {
		this.projectId = projectId;
		this.targetMemberId = targetMemberId;
		this.actorId = actorId;
		this.action = action;
		this.previousRole = previousRole;
		this.newRole = newRole;
	}

	public static ProjectMemberAuditLog added(Long projectId, Long targetMemberId, Long actorId, ProjectRole role) {
		return new ProjectMemberAuditLog(projectId, targetMemberId, actorId, ProjectMemberAction.ADDED, null, role);
	}

	public static ProjectMemberAuditLog roleChanged(Long projectId, Long targetMemberId, Long actorId,
			ProjectRole previousRole, ProjectRole newRole) {
		return new ProjectMemberAuditLog(
				projectId, targetMemberId, actorId, ProjectMemberAction.ROLE_CHANGED, previousRole, newRole);
	}

	public static ProjectMemberAuditLog removed(Long projectId, Long targetMemberId, Long actorId,
			ProjectRole previousRole) {
		return new ProjectMemberAuditLog(
				projectId, targetMemberId, actorId, ProjectMemberAction.REMOVED, previousRole, null);
	}
}
