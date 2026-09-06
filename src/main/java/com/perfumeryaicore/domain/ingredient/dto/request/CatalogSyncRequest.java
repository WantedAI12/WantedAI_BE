package com.perfumeryaicore.domain.ingredient.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 카탈로그 동기화 트리거. 카탈로그 자체는 전역이지만 권한 검증(FRAGRANCE_RND/ORG_ADMIN)과
 * Job 소속을 위해 프로젝트 컨텍스트를 받는다. (명세서 경로 {@code POST /ingredients/catalog-sync}에는
 * projectId가 없었음 — 문서 델타 반영 대상)
 */
public record CatalogSyncRequest(

		@NotNull
		Long projectId
) {
}
