package com.perfumeryaicore.domain.formula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.service.CandidateGenerationService;
import com.perfumeryaicore.domain.formula.service.CandidatePersistenceService;
import com.perfumeryaicore.domain.formula.service.FormulaRequestMapper;
import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobExecutor;
import com.perfumeryaicore.domain.job.service.JobExecutor.JobWork;
import com.perfumeryaicore.domain.job.service.JobService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse.Deployment;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse.RecipeLine;
import com.perfumeryaicore.global.client.dto.LotionDesignResponse;
import com.perfumeryaicore.global.client.dto.LotionEstimateRequest;
import com.perfumeryaicore.global.common.ProductCategory;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.common.TargetRegion;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateGenerationServiceTest {

	private final FragranceRequestService fragranceRequestService = mock(FragranceRequestService.class);
	private final JobService jobService = mock(JobService.class);
	private final JobExecutor jobExecutor = mock(JobExecutor.class);
	private final PerfumeryAiClient perfumeryAiClient = mock(PerfumeryAiClient.class);
	private final FormulaRequestMapper formulaRequestMapper = mock(FormulaRequestMapper.class);
	private final CandidatePersistenceService candidatePersistenceService = mock(CandidatePersistenceService.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);

	private CandidateGenerationService service;

	@BeforeEach
	void setUp() {
		service = new CandidateGenerationService(fragranceRequestService, jobService, jobExecutor,
				perfumeryAiClient, formulaRequestMapper, candidatePersistenceService, accessGuard);
		when(accessGuard.requireWriteRole(10L, 1L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND))
				.thenReturn(ProjectRole.PERFUMER);
	}

	private FragranceRequest confirmedRequest() {
		FragranceRequest request = FragranceRequest.create(10L, 1L, "citrus woody");
		ReflectionTestUtils.setField(request, "id", 5L);
		return request;
	}

	private FragranceRequest lotionRequest() {
		FragranceRequest request = FragranceRequest.create(10L, 1L, "citrus lotion");
		request.applyUpdate(null, ProductCategory.BODY_LOTION, TargetRegion.KR, 1,
				null, null, null, null, 150.0, null);
		ReflectionTestUtils.setField(request, "id", 5L);
		return request;
	}

	private Job jobWithId(long id) {
		Job job = Job.pending(10L, JobType.CANDIDATE_GENERATION, 1L, "5");
		ReflectionTestUtils.setField(job, "id", id);
		return job;
	}

	private static JobExecutor.JobContext context(boolean cancelled, Runnable onAiCallStarted) {
		return new JobExecutor.JobContext() {
			@Override
			public void aiCallStarted() {
				onAiCallStarted.run();
			}

			@Override
			public boolean isCancelled() {
				return cancelled;
			}
		};
	}

	@Test
	void enqueue_on_unconfirmed_request_never_creates_a_job() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L))
				.thenThrow(new BusinessException(ErrorCode.REQUEST_NOT_CONFIRMED));

		assertThatThrownBy(() -> service.enqueue(5L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REQUEST_NOT_CONFIRMED);

		verify(jobService, never()).enqueue(anyLong(), any(), anyLong(), any());
		verify(jobExecutor, never()).execute(anyLong(), any(), any());
	}

	@Test
	void enqueue_creates_job_and_dispatches_execution() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(confirmedRequest());
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5", null)).thenReturn(jobWithId(77L));
		JobResponse expected = new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING,
				false, null, null, null, null);
		when(jobService.get(77L, 1L)).thenReturn(expected);

		JobResponse actual = service.enqueue(5L, 1L);

		assertThat(actual).isEqualTo(expected);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), any());
	}

	/** BE-046: Idempotency-Key가 있으면 jobService에 그대로 전달해 중복 제출을 막는다. */
	@Test
	void enqueue_passes_the_idempotency_key_through_to_job_service() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(confirmedRequest());
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5", "client-key-1"))
				.thenReturn(jobWithId(77L));
		when(jobService.get(77L, 1L)).thenReturn(
				new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, false, null, null, null, null));

		service.enqueue(5L, 1L, "client-key-1");

		verify(jobService).enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5", "client-key-1");
	}

	@Test
	void successful_generation_persists_candidate_and_reports_ai_call_start() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(confirmedRequest());
		Job job = jobWithId(77L);
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5", null)).thenReturn(job);
		when(jobService.get(77L, 1L)).thenReturn(new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, false, null, null, null, null));

		FormulaGenerationRequest modalRequest = FormulaGenerationRequest.standard(
				"citrus woody", "EU", "eau_de_parfum", null, null, 12);
		when(formulaRequestMapper.toModalRequest(any(FragranceRequest.class))).thenReturn(modalRequest);

		FormulaGenerationResponse parsed = new FormulaGenerationResponse(
				"prototype_ready", "안전 조건 충족", "f-1", null, null, 42.0,
				List.of(new RecipeLine("dihydromyrcenol", "Dihydromyrcenol", "top", 23.5, 3.5, 18.0, 0.99)),
				List.of(0, 15, 60, 240, 480), null, null, null, "claim boundary text",
				null, "headspace-olfactory-twin-2.2", null,
				new Deployment("modal", "cpu", false, "wheel-sha", "registry-sha", 29240));
		PerfumeryAiResult aiResult = new PerfumeryAiResult("{\"status\":\"prototype_ready\"}", parsed, 1690L);
		// BE-048: 생산 코드가 이제 aiCallStarted를 직접 호출하지 않고, 클라이언트의 '게이트 통과 직후'
		// 콜백으로 넘긴다. 실제 클라이언트가 게이트 통과 시점에 그 콜백을 실행하는 것처럼 흉내낸다.
		when(perfumeryAiClient.generateFormula(eq(modalRequest), eq("job-77"), any())).thenAnswer(inv -> {
			((Runnable) inv.getArgument(2)).run();
			return aiResult;
		});
		when(candidatePersistenceService.persist(5L, 10L, 1L, 77L, aiResult)).thenReturn(900L);

		service.enqueue(5L, 1L);

		ArgumentCaptor<JobWork> captor = ArgumentCaptor.forClass(JobWork.class);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), captor.capture());

		boolean[] aiCallStarted = {false};
		Long resultRefId = captor.getValue().run(context(false, () -> aiCallStarted[0] = true));

		assertThat(resultRefId).isEqualTo(900L);
		assertThat(aiCallStarted[0]).isTrue();
		verify(candidatePersistenceService, times(1)).persist(5L, 10L, 1L, 77L, aiResult);
	}

	/** BE-035: 기권 진단 데이터는 별도로 보존하되, 후보로는 절대 만들지 않는다. */
	@Test
	void no_safe_match_is_rejected_without_creating_a_candidate_but_persists_the_rejection_diagnostics() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(confirmedRequest());
		Job job = jobWithId(77L);
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5", null)).thenReturn(job);
		when(jobService.get(77L, 1L)).thenReturn(new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, false, null, null, null, null));

		FormulaGenerationRequest modalRequest = FormulaGenerationRequest.standard(
				"citrus woody", "EU", "eau_de_parfum", null, null, 12);
		when(formulaRequestMapper.toModalRequest(any(FragranceRequest.class))).thenReturn(modalRequest);

		FormulaGenerationResponse rejected = new FormulaGenerationResponse(
				"no_safe_match", "허용 원료로는 안전 기준을 만족하는 배합이 없습니다.", null, null, null, null,
				List.of(), null, null, null, null, null, null, null, null, null);
		PerfumeryAiResult aiResult = new PerfumeryAiResult("{\"status\":\"no_safe_match\"}", rejected, 800L);
		when(perfumeryAiClient.generateFormula(eq(modalRequest), eq("job-77"), any())).thenReturn(aiResult);

		service.enqueue(5L, 1L);

		ArgumentCaptor<JobWork> captor = ArgumentCaptor.forClass(JobWork.class);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), captor.capture());

		assertThatThrownBy(() -> captor.getValue().run(context(false, () -> { })))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.GENERATION_REJECTED);
		verify(candidatePersistenceService, never()).persist(anyLong(), anyLong(), anyLong(), anyLong(), any());
		verify(candidatePersistenceService).persistRejection(5L, 10L, 77L, 1L,
				"no_safe_match", "허용 원료로는 안전 기준을 만족하는 배합이 없습니다.", "{\"status\":\"no_safe_match\"}");
	}

	/** BE-004: SUPPLIER/AUDITOR는 후보 생성을 트리거할 수 없다. */
	@Test
	void a_non_trigger_role_cannot_enqueue_generation() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(confirmedRequest());
		when(accessGuard.requireWriteRole(10L, 1L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PRODUCT_BRAND))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.enqueue(5L, 1L))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(jobService, never()).enqueue(anyLong(), any(), anyLong(), any());
		verify(jobExecutor, never()).execute(anyLong(), any(), any());
	}

	/** BE-041: 취소 후 늦게 온 AI 응답은 후보로 저장하지 않는다. */
	@Test
	void cancelled_before_persisting_is_rejected_without_saving_a_candidate() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(confirmedRequest());
		Job job = jobWithId(77L);
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5", null)).thenReturn(job);
		when(jobService.get(77L, 1L)).thenReturn(new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, false, null, null, null, null));

		FormulaGenerationRequest modalRequest = FormulaGenerationRequest.standard(
				"citrus woody", "EU", "eau_de_parfum", null, null, 12);
		when(formulaRequestMapper.toModalRequest(any(FragranceRequest.class))).thenReturn(modalRequest);

		FormulaGenerationResponse parsed = new FormulaGenerationResponse(
				"prototype_ready", "안전 조건 충족", "f-1", null, null, 42.0,
				List.of(new RecipeLine("dihydromyrcenol", "Dihydromyrcenol", "top", 23.5, 3.5, 18.0, 0.99)),
				List.of(0, 15, 60, 240, 480), null, null, null, "claim boundary text",
				null, "headspace-olfactory-twin-2.2", null,
				new Deployment("modal", "cpu", false, "wheel-sha", "registry-sha", 29240));
		PerfumeryAiResult aiResult = new PerfumeryAiResult("{\"status\":\"prototype_ready\"}", parsed, 1690L);
		when(perfumeryAiClient.generateFormula(eq(modalRequest), eq("job-77"), any())).thenReturn(aiResult);

		service.enqueue(5L, 1L);

		ArgumentCaptor<JobWork> captor = ArgumentCaptor.forClass(JobWork.class);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), captor.capture());

		assertThatThrownBy(() -> captor.getValue().run(context(true, () -> { })))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.JOB_CANCELLED);
		verify(candidatePersistenceService, never()).persist(anyLong(), anyLong(), anyLong(), anyLong(), any());
	}

	/** 바디로션 1단계: productCategory가 BODY_LOTION이면 표준 흐름이 아니라 로션 전용 경로로 간다. */
	@Test
	void a_lotion_request_is_routed_to_the_lotion_client_method_not_the_standard_one() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(lotionRequest());
		Job job = jobWithId(77L);
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5", null)).thenReturn(job);
		when(jobService.get(77L, 1L)).thenReturn(new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, false, null, null, null, null));

		LotionDesignResponse parsed = new LotionDesignResponse("ready", true, false, null,
				List.of(tools.jackson.databind.json.JsonMapper.builder().build().createObjectNode()
						.put("ingredient_id", "citral").put("name", "Citral")),
				List.of());
		PerfumeryAiResult<LotionDesignResponse> aiResult = new PerfumeryAiResult<>("{\"status\":\"ready\"}", parsed, 500L);
		when(perfumeryAiClient.designLotion(any(LotionEstimateRequest.class), eq("job-77"), any())).thenReturn(aiResult);
		when(candidatePersistenceService.persistLotion(5L, 10L, 1L, 77L, aiResult)).thenReturn(900L);

		service.enqueue(5L, 1L);

		ArgumentCaptor<JobWork> captor = ArgumentCaptor.forClass(JobWork.class);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), captor.capture());

		Long resultRefId = captor.getValue().run(context(false, () -> { }));

		assertThat(resultRefId).isEqualTo(900L);
		verify(perfumeryAiClient, never()).generateFormula(any(), any(), any());
		verify(candidatePersistenceService, never()).persist(anyLong(), anyLong(), anyLong(), anyLong(), any());
	}

	/** recipe가 비어있고 closest_candidate만 있으면(기권·탐색 미완료) 후보로 저장하면 안 된다. */
	@Test
	void a_lotion_response_that_is_not_a_usable_candidate_is_rejected_without_creating_a_candidate() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(lotionRequest());
		Job job = jobWithId(77L);
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5", null)).thenReturn(job);
		when(jobService.get(77L, 1L)).thenReturn(new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, false, null, null, null, null));

		LotionDesignResponse parsed = new LotionDesignResponse(
				"insufficient_observed_target_coverage", false, true, null, List.of(), List.of());
		PerfumeryAiResult<LotionDesignResponse> aiResult =
				new PerfumeryAiResult<>("{\"status\":\"insufficient_observed_target_coverage\"}", parsed, 500L);
		when(perfumeryAiClient.designLotion(any(LotionEstimateRequest.class), eq("job-77"), any())).thenReturn(aiResult);

		service.enqueue(5L, 1L);

		ArgumentCaptor<JobWork> captor = ArgumentCaptor.forClass(JobWork.class);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), captor.capture());

		assertThatThrownBy(() -> captor.getValue().run(context(false, () -> { })))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.GENERATION_REJECTED);
		verify(candidatePersistenceService, never()).persistLotion(anyLong(), anyLong(), anyLong(), anyLong(), any());
		verify(candidatePersistenceService).persistRejection(5L, 10L, 77L, 1L,
				"insufficient_observed_target_coverage", "insufficient_observed_target_coverage",
				"{\"status\":\"insufficient_observed_target_coverage\"}");
	}

	/**
	 * AI팀 확인(2026-09-17): 목표 유사도 미달(profile_target_met=false)도 유효한 계산 결과다 -
	 * 실제 배합이 있으면 후보로 저장하고, 목표 달성 여부는 별도로 보관한다(통과·승인으로 바뀌는 게
	 * 아님).
	 */
	@Test
	void a_lotion_response_below_the_similarity_target_is_still_saved_as_a_candidate() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(lotionRequest());
		Job job = jobWithId(77L);
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5", null)).thenReturn(job);
		when(jobService.get(77L, 1L)).thenReturn(
				new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, false, null, null, null, null));

		LotionDesignResponse parsed = new LotionDesignResponse("ready", false, false, null,
				List.of(tools.jackson.databind.json.JsonMapper.builder().build().createObjectNode()
						.put("ingredient_id", "citral").put("name", "Citral")),
				List.of());
		PerfumeryAiResult<LotionDesignResponse> aiResult = new PerfumeryAiResult<>("{\"status\":\"ready\"}", parsed, 500L);
		when(perfumeryAiClient.designLotion(any(LotionEstimateRequest.class), eq("job-77"), any())).thenReturn(aiResult);
		when(candidatePersistenceService.persistLotion(5L, 10L, 1L, 77L, aiResult)).thenReturn(900L);

		service.enqueue(5L, 1L);

		ArgumentCaptor<JobWork> captor = ArgumentCaptor.forClass(JobWork.class);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), captor.capture());

		Long candidateId = captor.getValue().run(context(false, () -> { }));

		assertThat(candidateId).isEqualTo(900L);
		verify(candidatePersistenceService, never()).persistRejection(anyLong(), anyLong(), anyLong(), anyLong(), any(), any(), any());
	}
}
