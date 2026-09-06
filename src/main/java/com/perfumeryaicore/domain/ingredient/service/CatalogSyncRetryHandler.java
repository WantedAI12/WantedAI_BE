package com.perfumeryaicore.domain.ingredient.service;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobRetryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code CATALOG_SYNC} 작업 재시도. {@code job.inputPayload}에 저장된 projectId로 다시 실행한다.
 */
@Component
@RequiredArgsConstructor
public class CatalogSyncRetryHandler implements JobRetryHandler {

	private final CatalogSyncService catalogSyncService;

	@Override
	public JobType supportedType() {
		return JobType.CATALOG_SYNC;
	}

	@Override
	public void redispatch(Job job) {
		Long projectId = Long.valueOf(job.getInputPayload());
		catalogSyncService.dispatch(job.getId(), projectId, job.getCreatedBy());
	}
}
