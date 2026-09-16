package com.perfumeryaicore.domain.project.dto.response;

/**
 * 프로젝트에 연결된 이미지 조회 응답. {@code imageUrl}은 짧게 유효한 서명 URL이며 저장되지 않는다.
 */
public record ProjectImageResponse(
		Long assetId,
		String imageUrl
) {
}
