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
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.safety.service.ApprovalGateService;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExperimentStatusServiceTest {

	private final CandidateService candidateService = mock(CandidateService.class);
	private final ApprovalGateService approvalGateService = mock(ApprovalGateService.class);
	private final ExperimentStatusLogRepository logRepository = mock(ExperimentStatusLogRepository.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final ExperimentStatusService service =
			new ExperimentStatusService(candidateService, approvalGateService, logRepository, accessGuard);

	@BeforeEach
	void actorHasATransitionRole() {
		when(candidateService.getProjectId(500L, 1L)).thenReturn(10L);
		when(accessGuard.requireRole(10L, 1L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PROJECT_MANAGER))
				.thenReturn(ProjectRole.PERFUMER);
	}

	private ExperimentStatusLog logEntry(CandidateStatus status) {
		ExperimentStatusLog entry = ExperimentStatusLog.record(500L, status, 1L);
		ReflectionTestUtils.setField(entry, "id", 1L);
		return entry;
	}

	@Test
	void confirming_for_experiment_requires_safety_gate_approval() {
		when(candidateService.getCurrentVersionId(500L, 1L)).thenReturn(900L);
		when(approvalGateService.isApprovedForVersion(500L, 900L)).thenReturn(false);

		assertThatThrownBy(() -> service.changeStatus(500L, 1L, CandidateStatus.CONFIRMED_FOR_EXPERIMENT))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SAFETY_GATE_NOT_APPROVED);

		verify(candidateService, never()).transitionStatus(any(), any(), any());
		verify(logRepository, never()).save(any());
	}

	@Test
	void confirming_for_experiment_succeeds_when_approved_and_logs_the_change() {
		when(candidateService.getCurrentVersionId(500L, 1L)).thenReturn(900L);
		when(approvalGateService.isApprovedForVersion(500L, 900L)).thenReturn(true);
		when(logRepository.save(any(ExperimentStatusLog.class)))
				.thenReturn(logEntry(CandidateStatus.CONFIRMED_FOR_EXPERIMENT));

		ExperimentStatusLogResponse response =
				service.changeStatus(500L, 1L, CandidateStatus.CONFIRMED_FOR_EXPERIMENT);

		verify(candidateService).transitionStatus(500L, 1L, CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
		assertThat(response.status()).isEqualTo(CandidateStatus.CONFIRMED_FOR_EXPERIMENT);
	}

	/** BE-032: 승인 이후 후보 버전이 바뀌었으면(과거 버전 대상 승인) 실험 확정을 거부한다. */
	@Test
	void confirming_for_experiment_is_blocked_when_the_approval_targeted_an_older_version() {
		when(candidateService.getCurrentVersionId(500L, 1L)).thenReturn(901L);
		when(approvalGateService.isApprovedForVersion(500L, 901L)).thenReturn(false);

		assertThatThrownBy(() -> service.changeStatus(500L, 1L, CandidateStatus.CONFIRMED_FOR_EXPERIMENT))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SAFETY_GATE_NOT_APPROVED);
	}

	@Test
	void other_transitions_do_not_consult_the_safety_gate() {
		when(logRepository.save(any(ExperimentStatusLog.class))).thenReturn(logEntry(CandidateStatus.REJECTED));

		service.changeStatus(500L, 1L, CandidateStatus.REJECTED);

		verify(approvalGateService, never()).isApprovedForVersion(any(), any());
		verify(candidateService).transitionStatus(500L, 1L, CandidateStatus.REJECTED);
	}

	/** BE-004: 실험 확정/상태 전이는 PERFUMER/FRAGRANCE_RND/PROJECT_MANAGER만 할 수 있다. */
	@Test
	void a_non_transition_role_is_forbidden_from_changing_status() {
		when(accessGuard.requireRole(10L, 1L, ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PROJECT_MANAGER))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.changeStatus(500L, 1L, CandidateStatus.REJECTED))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(candidateService, never()).transitionStatus(any(), any(), any());
		verify(logRepository, never()).save(any());
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
