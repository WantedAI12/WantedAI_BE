package com.perfumeryaicore.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.evidence.dto.response.EvidenceEvent;
import com.perfumeryaicore.domain.evidence.service.EvidenceTimelineService;
import com.perfumeryaicore.domain.experiment.dto.response.ExperimentStatusLogResponse;
import com.perfumeryaicore.domain.experiment.service.ExperimentStatusService;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.GenerationMeta;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.safety.dto.response.ApprovalGateResponse;
import com.perfumeryaicore.domain.safety.entity.ApprovalDecision;
import com.perfumeryaicore.domain.safety.service.ApprovalGateService;
import com.perfumeryaicore.global.common.CandidateStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceTimelineServiceTest {

	private final CandidateService candidateService = mock(CandidateService.class);
	private final ApprovalGateService approvalGateService = mock(ApprovalGateService.class);
	private final ExperimentStatusService experimentStatusService = mock(ExperimentStatusService.class);
	private final EvidenceTimelineService service =
			new EvidenceTimelineService(candidateService, approvalGateService, experimentStatusService);

	@Test
	void merges_version_gate_and_status_events_in_chronological_order() {
		LocalDateTime t0 = LocalDateTime.of(2026, 9, 1, 10, 0);
		LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 11, 0);
		LocalDateTime t2 = LocalDateTime.of(2026, 9, 1, 12, 0);

		when(candidateService.versions(900L, 1L)).thenReturn(List.of(new CandidateVersionResponse(
				1200L, 900L, null, List.of(), 42.0, "근거", new GenerationMeta("modal", false, "prototype_ready", 1L),
				null, 7L, t0)));
		when(approvalGateService.history(900L, 1L)).thenReturn(List.of(new ApprovalGateResponse(
				1L, 900L, ApprovalDecision.APPROVED, "IFRA 충족", 3L, t2)));
		when(experimentStatusService.history(900L, 1L)).thenReturn(List.of(new ExperimentStatusLogResponse(
				900L, CandidateStatus.CONFIRMED_FOR_EXPERIMENT, 5L, t1)));

		List<EvidenceEvent> timeline = service.timeline(900L, 1L);

		verify(candidateService).versions(900L, 1L);
		assertThat(timeline).extracting(EvidenceEvent::occurredAt).containsExactly(t0, t1, t2);
		assertThat(timeline).extracting(EvidenceEvent::action).containsExactly(
				"CANDIDATE_VERSION_CREATED", "EXPERIMENT_STATUS_CONFIRMED_FOR_EXPERIMENT", "APPROVAL_GATE_APPROVED");
		assertThat(timeline.get(0).candidateVersionId()).isEqualTo(1200L);
	}

	@Test
	void empty_history_yields_empty_timeline() {
		when(candidateService.versions(900L, 1L)).thenReturn(List.of());
		when(approvalGateService.history(900L, 1L)).thenReturn(List.of());
		when(experimentStatusService.history(900L, 1L)).thenReturn(List.of());

		assertThat(service.timeline(900L, 1L)).isEmpty();
	}
}
