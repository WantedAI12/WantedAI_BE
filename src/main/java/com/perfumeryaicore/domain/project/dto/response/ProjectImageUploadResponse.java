package com.perfumeryaicore.domain.project.dto.response;

import com.perfumeryaicore.domain.project.entity.ProjectImageAsset;
import com.perfumeryaicore.domain.project.entity.ProjectImageAssetStatus;

/**
 * 임시 업로드 직후 응답. 아직 어떤 프로젝트에도 연결되지 않은 자산의 ID를 담는다 —
 * 클라이언트는 이 ID로 {@code PUT /projects/{projectId}/image}를 호출해 연결을 마무리한다.
 */
public record ProjectImageUploadResponse(
		Long assetId,
		ProjectImageAssetStatus status
) {

	public static ProjectImageUploadResponse from(ProjectImageAsset asset) {
		return new ProjectImageUploadResponse(asset.getId(), asset.getStatus());
	}
}
