package com.perfumeryaicore.domain.ingredient.service;

import com.perfumeryaicore.domain.ingredient.dto.response.CatalogSyncResultResponse;
import com.perfumeryaicore.domain.ingredient.entity.CatalogSyncRun;
import com.perfumeryaicore.domain.ingredient.repository.CatalogSyncRunRepository;
import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobExecutor;
import com.perfumeryaicore.domain.job.service.JobService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 조향 AI {@code GET /v1/catalog} 스냅샷 동기화. 후보 생성과 같은 Job 패턴을 쓴다:
 * 트리거 API는 즉시 {@code jobId} 반환 → 비동기로 Modal 호출 → 스냅샷 원문·대표 통계 저장.
 *
 * <p>카탈로그는 전역이지만 권한(FRAGRANCE_RND/ORG_ADMIN)과 Job 소속을 위해 projectId를 받는다.
 */
@Slf4j
@Service
public class CatalogSyncService {

	private final JobService jobService;
	private final JobExecutor jobExecutor;
	private final PerfumeryAiClient perfumeryAiClient;
	private final CatalogSyncRunRepository catalogSyncRunRepository;
	private final ProjectAccessGuard accessGuard;
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	/** {@code jobService}는 {@code @Lazy} — {@link CatalogSyncRetryHandler}가 이 서비스를 다시
	 * 참조하는 순환 구조라서 생성자를 먼저 완료시켜야 한다({@code CandidateGenerationService}와 동일). */
	public CatalogSyncService(
			@Lazy JobService jobService,
			JobExecutor jobExecutor,
			PerfumeryAiClient perfumeryAiClient,
			CatalogSyncRunRepository catalogSyncRunRepository,
			ProjectAccessGuard accessGuard) {
		this.jobService = jobService;
		this.jobExecutor = jobExecutor;
		this.perfumeryAiClient = perfumeryAiClient;
		this.catalogSyncRunRepository = catalogSyncRunRepository;
		this.accessGuard = accessGuard;
	}

	public JobResponse sync(Long memberId, Long projectId) {
		accessGuard.requireRole(projectId, memberId, ProjectRole.FRAGRANCE_RND, ProjectRole.ORG_ADMIN);
		Job job = jobService.enqueue(projectId, JobType.CATALOG_SYNC, memberId, String.valueOf(projectId));
		dispatch(job.getId(), projectId, memberId);
		return jobService.get(job.getId(), memberId);
	}

	/** 최초 실행과 재시도가 공유하는 실행 경로. */
	public void dispatch(Long jobId, Long projectId, Long memberId) {
		jobExecutor.execute(jobId, JobType.CATALOG_SYNC, context -> run(jobId, projectId, memberId, context));
	}

	private Long run(Long jobId, Long projectId, Long memberId, JobExecutor.JobContext context) {
		context.aiCallStarted();
		String raw = perfumeryAiClient.catalogRaw("job-" + jobId);
		JsonNode snapshot = parse(raw);

		CatalogSyncRun saved = catalogSyncRunRepository.save(CatalogSyncRun.completed(
				jobId, projectId, raw,
				intOrNull(snapshot, "reference_molecules"),
				intOrNull(snapshot, "safety_screened"),
				intOrNull(snapshot, "prototype_active_total"),
				textOrNull(snapshot, "catalog_version"),
				textOrNull(snapshot, "registry_sha256"),
				memberId));
		log.info("[CATALOG-SYNC] job={} run={} referenceMolecules={} version={}",
				jobId, saved.getId(), saved.getReferenceMoleculeCount(), saved.getCatalogVersion());
		return saved.getId();
	}

	@Transactional(readOnly = true)
	public CatalogSyncResultResponse getResult(Long jobId, Long memberId) {
		JobResponse job = jobService.get(jobId, memberId); // 접근 제어 + 존재 확인
		return catalogSyncRunRepository.findByJobId(jobId)
				.map(run -> CatalogSyncResultResponse.of(run, parseOrNull(run.getRawSnapshot())))
				.orElseGet(() -> CatalogSyncResultResponse.pending(jobId, job.status()));
	}

	private JsonNode parse(String raw) {
		try {
			return jsonMapper.readTree(raw);
		} catch (JacksonException e) {
			log.error("[CATALOG-SYNC] snapshot parse failure: {}", e.getMessage());
			throw new BusinessException(ErrorCode.AI_SCHEMA_VERSION_MISMATCH);
		}
	}

	private JsonNode parseOrNull(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return jsonMapper.readTree(raw);
		} catch (JacksonException e) {
			return null;
		}
	}

	private static Integer intOrNull(JsonNode node, String field) {
		JsonNode v = node.get(field);
		return v != null && v.isIntegralNumber() ? v.asInt() : null;
	}

	private static String textOrNull(JsonNode node, String field) {
		JsonNode v = node.get(field);
		return v != null && v.isString() ? v.asString() : null;
	}
}
