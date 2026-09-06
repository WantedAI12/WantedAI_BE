package com.perfumeryaicore.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.evidence.entity.EvidenceReport;
import com.perfumeryaicore.domain.evidence.repository.EvidenceReportRepository;
import com.perfumeryaicore.domain.evidence.service.EvidenceReportPdfRenderer;
import com.perfumeryaicore.domain.evidence.service.EvidenceReportService;
import com.perfumeryaicore.domain.evidence.service.EvidenceTimelineService;
import com.perfumeryaicore.domain.evidence.service.SensoryTestService;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.job.dto.response.JobResponse;
import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobExecutor;
import com.perfumeryaicore.domain.job.service.JobExecutor.JobWork;
import com.perfumeryaicore.domain.job.service.JobService;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.domain.safety.service.SafetyEvaluationService;
import com.perfumeryaicore.global.storage.S3FileStorage;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class EvidenceReportServiceTest {

	private final CandidateService candidateService = mock(CandidateService.class);
	private final SafetyEvaluationService safetyEvaluationService = mock(SafetyEvaluationService.class);
	private final PredictionService predictionService = mock(PredictionService.class);
	private final EvidenceTimelineService evidenceTimelineService = mock(EvidenceTimelineService.class);
	private final SensoryTestService sensoryTestService = mock(SensoryTestService.class);
	private final EvidenceReportRepository evidenceReportRepository = mock(EvidenceReportRepository.class);
	private final EvidenceReportPdfRenderer pdfRenderer = mock(EvidenceReportPdfRenderer.class);
	private final S3FileStorage s3FileStorage = mock(S3FileStorage.class);
	private final JobService jobService = mock(JobService.class);
	private final JobExecutor jobExecutor = mock(JobExecutor.class);

	private final EvidenceReportService service = new EvidenceReportService(
			candidateService, safetyEvaluationService, predictionService, evidenceTimelineService,
			sensoryTestService, evidenceReportRepository, pdfRenderer, s3FileStorage, jobService, jobExecutor);

	private Job job(long id) {
		Job job = Job.pending(10L, JobType.EVIDENCE_REPORT, 1L, "900");
		ReflectionTestUtils.setField(job, "id", id);
		return job;
	}

	@Test
	void request_enqueues_job_and_dispatches_generation() {
		when(candidateService.getProjectId(900L, 1L)).thenReturn(10L);
		when(jobService.enqueue(10L, JobType.EVIDENCE_REPORT, 1L, "900")).thenReturn(job(88L));
		JobResponse expected = new JobResponse(88L, JobType.EVIDENCE_REPORT, JobStatus.PENDING,
				false, null, null, null, null);
		when(jobService.get(88L, 1L)).thenReturn(expected);

		JobResponse actual = service.request(900L, 1L);

		assertThat(actual).isEqualTo(expected);
		verify(jobExecutor).execute(eq(88L), eq(JobType.EVIDENCE_REPORT), any());
	}

	@Test
	void generation_renders_pdf_uploads_to_s3_and_saves_report() {
		when(candidateService.getProjectId(900L, 1L)).thenReturn(10L);
		when(jobService.enqueue(10L, JobType.EVIDENCE_REPORT, 1L, "900")).thenReturn(job(88L));
		when(jobService.get(88L, 1L)).thenReturn(new JobResponse(88L, JobType.EVIDENCE_REPORT,
				JobStatus.PENDING, false, null, null, null, null));
		when(evidenceTimelineService.timeline(900L, 1L)).thenReturn(List.of());
		when(sensoryTestService.list(900L, 1L)).thenReturn(List.of());
		when(pdfRenderer.render(any())).thenReturn(new byte[] {'%', 'P', 'D', 'F'});
		when(evidenceReportRepository.save(any(EvidenceReport.class))).thenAnswer(inv -> {
			EvidenceReport r = inv.getArgument(0);
			ReflectionTestUtils.setField(r, "id", 500L);
			return r;
		});

		service.request(900L, 1L);

		ArgumentCaptor<JobWork> captor = ArgumentCaptor.forClass(JobWork.class);
		verify(jobExecutor).execute(eq(88L), eq(JobType.EVIDENCE_REPORT), captor.capture());

		Long reportId = captor.getValue().run(() -> { });

		assertThat(reportId).isEqualTo(500L);
		verify(s3FileStorage).upload(eq("evidence-reports/900/88.pdf"), any(), eq("application/pdf"));

		ArgumentCaptor<EvidenceReport> reportCaptor = ArgumentCaptor.forClass(EvidenceReport.class);
		verify(evidenceReportRepository).save(reportCaptor.capture());
		assertThat(reportCaptor.getValue().getPdfObjectKey()).isEqualTo("evidence-reports/900/88.pdf");
	}

	@Test
	void get_builds_a_fresh_presigned_download_url() {
		EvidenceReport report = EvidenceReport.completed(900L, 88L, "{\"candidateId\":900}",
				"evidence-reports/900/88.pdf", 1L);
		ReflectionTestUtils.setField(report, "id", 500L);
		when(evidenceReportRepository.findById(500L)).thenReturn(Optional.of(report));
		when(s3FileStorage.presignedGetUrl(eq("evidence-reports/900/88.pdf"), any(Duration.class)))
				.thenReturn("https://s3.example/signed");

		var response = service.get(500L, 1L);

		verify(candidateService).assertAccessible(900L, 1L);
		assertThat(response.fileUrl()).isEqualTo("https://s3.example/signed");
		assertThat(response.reportData().get("candidateId").asInt()).isEqualTo(900);
	}
}
