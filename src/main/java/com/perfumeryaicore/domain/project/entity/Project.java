package com.perfumeryaicore.domain.project.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 프로젝트(테넌트). 모든 하위 도메인 리소스(요청/후보/원료 등)는 이 프로젝트 스코프 안에서만 조회된다.
 */
@Entity
@Getter
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseTimeEntity {

	public static final int NAME_MAX = 100;
	public static final int DESCRIPTION_MAX = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = NAME_MAX)
	private String name;

	@Column(length = DESCRIPTION_MAX)
	private String description;

	private Project(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public static Project create(String name, String description) {
		return new Project(name, description);
	}

	/** 부분 수정. {@code null}이 아닌 값만 반영한다. */
	public void updateInfo(String name, String description) {
		if (name != null) {
			this.name = name;
		}
		if (description != null) {
			this.description = description;
		}
	}
}
