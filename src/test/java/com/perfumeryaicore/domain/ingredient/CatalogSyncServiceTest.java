package com.perfumeryaicore.domain.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.ingredient.dto.response.CatalogSyncResultResponse;
import com.perfumeryaicore.domain.ingredient.entity.CatalogSyncRun;
import com.perfumeryaicore.domain.ingredient.repository.CatalogSyncRunRepository;
import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobExecutor;
import com.perfumeryaicore.domain.job.service.JobService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CatalogSyncServiceTest {

	private final JobService jobService = mock(JobService.class);
	private final JobExecutor jobExecutor = mock(JobExecutor.class);
	private final PerfumeryAiClient aiClient = mock(PerfumeryAiClient.class);
	private final CatalogSyncRunRepository runRepository = mock(CatalogSyncRunRepository.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final com.perfumeryaicore.domain.ingredient.service.CatalogSyncService service =
			new com.perfumeryaicore.domain.ingredient.service.CatalogSyncService(
					jobService, jobExecutor, aiClient, runRepository, accessGuard);

	@Test
	void sync_is_forbidden_for_a_role_other_than_fragrance_rnd_or_org_admin() {
		when(accessGuard.requireRole(eq(10L), eq(1L), any(ProjectRole.class), any(ProjectRole.class)))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.sync(1L, 10L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
	}

	@Test
	void sync_enqueues_a_catalog_sync_job_and_returns_its_status() {
		com.perfumeryaicore.domain.job.entity.Job job = mock(com.perfumeryaicore.domain.job.entity.Job.class);
		when(job.getId()).thenReturn(77L);
		when(jobService.enqueue(10L, JobType.CATALOG_SYNC, 1L, "10")).thenReturn(job);
		JobResponse response = new JobResponse(77L, JobType.CATALOG_SYNC, JobStatus.PENDING,
				false, null, null, LocalDateTime.now(), LocalDateTime.now());
		when(jobService.get(77L, 1L)).thenReturn(response);

		assertThat(service.sync(1L, 10L).jobId()).isEqualTo(77L);
	}

	@Test
	void get_result_is_pending_until_the_run_row_exists() {
		JobResponse running = new JobResponse(77L, JobType.CATALOG_SYNC, JobStatus.RUNNING,
				false, null, null, LocalDateTime.now(), LocalDateTime.now());
		when(jobService.get(77L, 1L)).thenReturn(running);
		when(runRepository.findByJobId(77L)).thenReturn(Optional.empty());

		CatalogSyncResultResponse result = service.getResult(77L, 1L);

		assertThat(result.status()).isEqualTo(JobStatus.RUNNING);
		assertThat(result.snapshot()).isNull();
		assertThat(result.referenceCount()).isNull();
	}

	@Test
	void get_result_exposes_counts_and_raw_snapshot_once_the_run_is_stored() {
		JobResponse succeeded = new JobResponse(77L, JobType.CATALOG_SYNC, JobStatus.SUCCEEDED,
				false, null, 5L, LocalDateTime.now(), LocalDateTime.now());
		when(jobService.get(77L, 1L)).thenReturn(succeeded);
		CatalogSyncRun run = CatalogSyncRun.completed(77L, 10L,
				"{\"reference_molecules\":29240,\"safety_screened\":29240,\"catalog_version\":\"v1\"}",
				29240, 29240, 34, "v1", "abc123", 1L);
		when(runRepository.findByJobId(77L)).thenReturn(Optional.of(run));

		CatalogSyncResultResponse result = service.getResult(77L, 1L);

		assertThat(result.referenceCount()).isEqualTo(29240);
		assertThat(result.screenedCount()).isEqualTo(29240);
		assertThat(result.activeTierCount()).isEqualTo(34);
		assertThat(result.snapshot().get("reference_molecules").asInt()).isEqualTo(29240);
	}
}
