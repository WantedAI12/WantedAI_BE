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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트 멤버십과 역할. {@code (project_id, member_id)}는 유일하다 — 한 사람은 한 프로젝트에서 역할 하나.
 */
@Entity
@Getter
@Table(
		name = "project_members",
		uniqueConstraints = @UniqueConstraint(
				name = "uq_project_members_project_member",
				columnNames = {"project_id", "member_id"}),
		indexes = @Index(name = "idx_project_members_member_id", columnList = "member_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "member_id", nullable = false)
	private Long memberId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProjectRole role;

	private ProjectMember(Long projectId, Long memberId, ProjectRole role) {
		this.projectId = projectId;
		this.memberId = memberId;
		this.role = role;
	}

	public static ProjectMember create(Long projectId, Long memberId, ProjectRole role) {
		return new ProjectMember(projectId, memberId, role);
	}

	public void changeRole(ProjectRole role) {
		this.role = role;
	}

	public boolean isAdmin() {
		return role == ProjectRole.ORG_ADMIN;
	}
}
