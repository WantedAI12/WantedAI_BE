package com.perfumeryaicore.domain.experiment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.experiment.dto.response.ExperimentStatusLogResponse;
import com.perfumeryaicore.domain.experiment.entity.ExperimentStatusLog;
import com.perfumeryaicore.domain.experiment.repository.ExperimentStatusLogRepository;
import com.perfumeryaicore.domain.experiment.service.ExperimentStatusService;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.safety.service.ApprovalGateService;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExperimentStatusServiceTest {

	private final CandidateService candidateService = mock(CandidateService.class);
	private final ApprovalGateService approvalGateService = mock(ApprovalGateService.class);
	private final ExperimentStatusLogRepository logRepository = mock(ExperimentStatusLogRepository.class);
	private final ExperimentStatusService service =
			new ExperimentStatusService(candidateService, approvalGateService, logRepository);

	private ExperimentStatusLog logEntry(CandidateStatus status) {
		ExperimentStatusLog entry = ExperimentStatusLog.record(500L, status, 1L);
		ReflectionTestUtils.setField(entry, "id", 1L);
		return entry;
	}

	@Test
	void confirming_for_experiment_requires_safety_gate_approval() {
		when(approvalGateService.isApproved(500L)).thenReturn(false);

		assertThatThrownBy(() -> service.changeStatus(500L, 1L, CandidateStatus.CONFIRMED_FOR_EXPERIMENT))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SAFETY_GATE_NOT_APPROVED);

		verify(candidateService, never()).transitionStatus(any(), any(), any());
		verify(logRepository, never()).save(any());
	}

	@Test
	void confirming_for_experiment_succeeds_when_approved_and_logs_the_change() {
		when(approvalGateService.isApproved(500L)).thenReturn(true);
		when(logRepository.save(any(ExperimentStatusLog.class)))
				.thenReturn(logEntry(CandidateStatus.CONFIRMED_FOR_EXPERIMENT));

		ExperimentStatusLogResponse response =
				service.changeStatus(500L, 1L, CandidateStatus.CONFIRMED_FOR_EXPERIMENT);

		verify(candidateService).transitionStatus(500L, 1L, CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
		assertThat(response.status()).isEqualTo(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
	}

	@Test
	void other_transitions_do_not_consult_the_safety_gate() {
		when(logRepository.save(any(ExperimentStatusLog.class))).thenReturn(logEntry(CandidateStatus.REJECTED));

		service.changeStatus(500L, 1L, CandidateStatus.REJECTED);

		verify(approvalGateService, never()).isApproved(500L);
		verify(candidateService).transitionStatus(500L, 1L, CandidateStatus.REJECTED);
	}

	@Test
	void history_checks_access_then_lists_most_recent_first() {
		when(logRepository.findByCandidateIdOrderByCreatedAtDesc(500L))
				.thenReturn(List.of(logEntry(CandidateStatus.IN_SENSORY_TEST), logEntry(CandidateStatus.CONFIRMED_FOR_EXPERIMENT)));

		List<ExperimentStatusLogResponse> history = service.history(500L, 1L);

		verify(candidateService).assertAccessible(500L, 1L);
		assertThat(history).hasSize(2);
	}
}
