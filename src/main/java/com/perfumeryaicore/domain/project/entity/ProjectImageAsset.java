package com.perfumeryaicore.domain.project.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
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
 * 프로젝트 이미지로 업로드된 S3 오브젝트 한 건. 업로드 시점에는 어느 프로젝트에도 속하지 않은
 * {@link ProjectImageAssetStatus#PENDING} 상태이고, {@code PUT /projects/{id}/image}로 연결되면
 * {@link ProjectImageAssetStatus#ATTACHED}가 된다. 교체·해제되면 {@link ProjectImageAssetStatus#ORPHANED}로
 * 남는다(오브젝트 자체는 즉시 지우지 않는다 — 후속 정리 배치의 몫).
 */
@Entity
@Getter
@Table(
		name = "project_image_assets",
		indexes = @Index(name = "idx_project_image_assets_project_id", columnList = "project_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectImageAsset extends BaseTimeEntity {

	private static final long MAX_BYTES = 5L * 1024 * 1024;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 연결된 프로젝트. 업로드 직후(PENDING)에는 {@code null}. */
	@Column(name = "project_id")
	private Long projectId;

	@Column(name = "object_key", nullable = false, length = 500)
	private String objectKey;

	@Column(name = "content_type", nullable = false, length = 100)
	private String contentType;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProjectImageAssetStatus status;

	@Column(name = "uploaded_by", nullable = false)
	private Long uploadedBy;

	private ProjectImageAsset(String objectKey, String contentType, long sizeBytes, Long uploadedBy) {
		this.objectKey = objectKey;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.uploadedBy = uploadedBy;
		this.status = ProjectImageAssetStatus.PENDING;
	}

	public static ProjectImageAsset pending(String objectKey, String contentType, long sizeBytes, Long uploadedBy) {
		if (sizeBytes > MAX_BYTES) {
			throw new BusinessException(ErrorCode.PROJECT_IMAGE_TOO_LARGE);
		}
		return new ProjectImageAsset(objectKey, contentType, sizeBytes, uploadedBy);
	}

	public boolean isUploadedBy(Long memberId) {
		return uploadedBy.equals(memberId);
	}

	/** PENDING 자산만 연결할 수 있다 — 이미 연결/폐기된 자산을 재사용하려 하면 막는다. */
	public void attachTo(Long projectId) {
		if (status != ProjectImageAssetStatus.PENDING) {
			throw new BusinessException(ErrorCode.PROJECT_IMAGE_ASSET_NOT_PENDING);
		}
		this.projectId = projectId;
		this.status = ProjectImageAssetStatus.ATTACHED;
	}

	/** 교체·해제로 더 이상 어떤 프로젝트에도 연결되지 않음. */
	public void markOrphaned() {
		this.status = ProjectImageAssetStatus.ORPHANED;
	}
}
