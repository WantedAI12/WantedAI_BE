package com.perfumeryaicore.domain.ingredient.dto.response;

import com.perfumeryaicore.domain.ingredient.entity.CatalogSyncRun;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

/**
 * 카탈로그 동기화 작업 상태 + 통계.
 *
 * <p>{@code referenceCount / screenedCount / activeTierCount}는 {@code /v1/catalog} 스냅샷의
 * {@code reference_molecules / safety_screened / prototype_active_total} 값에 각각 대응한다.
 * 전체 스냅샷 원문은 {@code snapshot}에 그대로 실어 보낸다.
 *
 * @param status 작업이 아직 끝나지 않았으면 통계·snapshot은 {@code null}
 */
public record CatalogSyncResultResponse(
		Long jobId,
		JobStatus status,
		Integer referenceCount,
		Integer screenedCount,
		Integer activeTierCount,
		String catalogVersion,
		String registrySha256,
		JsonNode snapshot,
		LocalDateTime syncedAt
) {

	public static CatalogSyncResultResponse pending(Long jobId, JobStatus status) {
		return new CatalogSyncResultResponse(jobId, status, null, null, null, null, null, null, null);
	}

	public static CatalogSyncResultResponse of(CatalogSyncRun run, JsonNode snapshot) {
		return new CatalogSyncResultResponse(
				run.getJobId(),
				run.getStatus(),
				run.getReferenceMoleculeCount(),
				run.getSafetyScreenedCount(),
				run.getPrototypeActiveCount(),
				run.getCatalogVersion(),
				run.getRegistrySha256(),
				snapshot,
				run.getSyncedAt());
	}
}
