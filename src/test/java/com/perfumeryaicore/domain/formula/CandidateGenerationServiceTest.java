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
import com.perfumeryaicore.domain.request.entity.FragranceRequest;
import com.perfumeryaicore.domain.request.service.FragranceRequestService;
import com.perfumeryaicore.global.client.PerfumeryAiClient;
import com.perfumeryaicore.global.client.PerfumeryAiResult;
import com.perfumeryaicore.global.client.dto.FormulaGenerationRequest;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse.Deployment;
import com.perfumeryaicore.global.client.dto.FormulaGenerationResponse.RecipeLine;
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

	private CandidateGenerationService service;

	@BeforeEach
	void setUp() {
		service = new CandidateGenerationService(fragranceRequestService, jobService, jobExecutor,
				perfumeryAiClient, formulaRequestMapper, candidatePersistenceService);
	}

	private FragranceRequest confirmedRequest() {
		FragranceRequest request = FragranceRequest.create(10L, 1L, "citrus woody");
		ReflectionTestUtils.setField(request, "id", 5L);
		return request;
	}

	private Job jobWithId(long id) {
		Job job = Job.pending(10L, JobType.CANDIDATE_GENERATION, 1L, "5");
		ReflectionTestUtils.setField(job, "id", id);
		return job;
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
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5")).thenReturn(jobWithId(77L));
		JobResponse expected = new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING,
				false, null, null, null, null);
		when(jobService.get(77L, 1L)).thenReturn(expected);

		JobResponse actual = service.enqueue(5L, 1L);

		assertThat(actual).isEqualTo(expected);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), any());
	}

	@Test
	void successful_generation_persists_candidate_and_reports_ai_call_start() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(confirmedRequest());
		Job job = jobWithId(77L);
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5")).thenReturn(job);
		when(jobService.get(77L, 1L)).thenReturn(new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, false, null, null, null, null));

		FormulaGenerationRequest modalRequest = FormulaGenerationRequest.standard(
				"citrus woody", "EU", "eau_de_parfum", null, null, 12);
		when(formulaRequestMapper.toModalRequest(any(FragranceRequest.class))).thenReturn(modalRequest);

		FormulaGenerationResponse parsed = new FormulaGenerationResponse(
				"prototype_ready", "안전 조건 충족", "f-1", 0.9, 42.0,
				List.of(new RecipeLine("dihydromyrcenol", "Dihydromyrcenol", "top", 23.5, 3.5, 18.0, 0.99)),
				List.of(0, 15, 60, 240, 480), null, null, null, "claim boundary text",
				null, "headspace-olfactory-twin-2.2",
				new Deployment("modal", "cpu", false, "wheel-sha", "registry-sha", 29240));
		PerfumeryAiResult aiResult = new PerfumeryAiResult("{\"status\":\"prototype_ready\"}", parsed, 1690L);
		when(perfumeryAiClient.generateFormula(eq(modalRequest), eq("job-77"))).thenReturn(aiResult);
		when(candidatePersistenceService.persist(5L, 1L, 77L, aiResult)).thenReturn(900L);

		service.enqueue(5L, 1L);

		ArgumentCaptor<JobWork> captor = ArgumentCaptor.forClass(JobWork.class);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), captor.capture());

		boolean[] aiCallStarted = {false};
		Long resultRefId = captor.getValue().run(() -> aiCallStarted[0] = true);

		assertThat(resultRefId).isEqualTo(900L);
		assertThat(aiCallStarted[0]).isTrue();
		verify(candidatePersistenceService, times(1)).persist(5L, 1L, 77L, aiResult);
	}

	@Test
	void no_safe_match_is_rejected_without_persisting() {
		when(fragranceRequestService.getConfirmedRequest(5L, 1L)).thenReturn(confirmedRequest());
		Job job = jobWithId(77L);
		when(jobService.enqueue(10L, JobType.CANDIDATE_GENERATION, 1L, "5")).thenReturn(job);
		when(jobService.get(77L, 1L)).thenReturn(new JobResponse(77L, JobType.CANDIDATE_GENERATION, JobStatus.PENDING, false, null, null, null, null));

		FormulaGenerationRequest modalRequest = FormulaGenerationRequest.standard(
				"citrus woody", "EU", "eau_de_parfum", null, null, 12);
		when(formulaRequestMapper.toModalRequest(any(FragranceRequest.class))).thenReturn(modalRequest);

		FormulaGenerationResponse rejected = new FormulaGenerationResponse(
				"no_safe_match", "허용 원료로는 안전 기준을 만족하는 배합이 없습니다.", null, null, null,
				List.of(), null, null, null, null, null, null, null, null);
		PerfumeryAiResult aiResult = new PerfumeryAiResult("{\"status\":\"no_safe_match\"}", rejected, 800L);
		when(perfumeryAiClient.generateFormula(eq(modalRequest), eq("job-77"))).thenReturn(aiResult);

		service.enqueue(5L, 1L);

		ArgumentCaptor<JobWork> captor = ArgumentCaptor.forClass(JobWork.class);
		verify(jobExecutor).execute(eq(77L), eq(JobType.CANDIDATE_GENERATION), captor.capture());

		assertThatThrownBy(() -> captor.getValue().run(() -> { }))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.GENERATION_REJECTED);
		verify(candidatePersistenceService, never()).persist(anyLong(), anyLong(), anyLong(), any());
	}
}
