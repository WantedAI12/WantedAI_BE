package com.perfumeryaicore.domain.project.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
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

	/** 현재 연결된 {@code ProjectImageAsset}의 ID. 연결된 이미지가 없으면 {@code null}. */
	@Column(name = "image_asset_id")
	private Long imageAssetId;

	private Project(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public static Project create(String name, String description) {
		return new Project(name, description);
	}

	/**
	 * 부분 수정. {@code null}이 아닌 값만 반영한다. 이름은 지정하면(null이 아니면) 공백일 수
	 * 없다 — 생성 시 {@code @NotBlank}와 달리 PATCH는 "값 없음(null)"과 "빈 값으로 바꿈"을
	 * 구분해야 해서 DTO 애노테이션만으로는 막을 수 없다(BE-084).
	 */
	public void updateInfo(String name, String description) {
		if (name != null) {
			if (name.isBlank()) {
				throw new BusinessException(ErrorCode.VALIDATION_FAILED, "프로젝트 이름은 공백일 수 없습니다.");
			}
			this.name = name;
		}
		if (description != null) {
			this.description = description;
		}
	}

	public void attachImage(Long assetId) {
		this.imageAssetId = assetId;
	}

	public void clearImage() {
		this.imageAssetId = null;
	}
}
