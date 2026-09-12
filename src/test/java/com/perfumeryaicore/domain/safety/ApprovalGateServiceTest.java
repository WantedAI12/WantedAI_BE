package com.perfumeryaicore.domain.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.safety.dto.request.ApprovalGateCreateRequest;
import com.perfumeryaicore.domain.safety.dto.response.ApprovalGateResponse;
import com.perfumeryaicore.domain.safety.entity.ApprovalDecision;
import com.perfumeryaicore.domain.safety.entity.ApprovalGate;
import com.perfumeryaicore.domain.safety.repository.ApprovalGateRepository;
import com.perfumeryaicore.domain.safety.service.ApprovalGateService;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ApprovalGateServiceTest {

	private final ApprovalGateRepository repository = mock(ApprovalGateRepository.class);
	private final CandidateService candidateService = mock(CandidateService.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final ApprovalGateService service = new ApprovalGateService(repository, candidateService, accessGuard);

	private ApprovalGate gate(long id, ApprovalDecision decision) {
		ApprovalGate gate = ApprovalGate.register(500L, decision, "comment", 1L);
		ReflectionTestUtils.setField(gate, "id", id);
		return gate;
	}

	@Test
	void register_checks_reviewer_role_then_saves_decision() {
		when(candidateService.getProjectId(500L, 1L)).thenReturn(10L);
		when(accessGuard.requireRole(10L, 1L, ProjectRole.SAFETY_REVIEWER)).thenReturn(ProjectRole.SAFETY_REVIEWER);
		when(repository.save(any(ApprovalGate.class))).thenAnswer(inv -> {
			ApprovalGate g = inv.getArgument(0);
			ReflectionTestUtils.setField(g, "id", 1L);
			return g;
		});

		ApprovalGateResponse response = service.register(500L, 1L,
				new ApprovalGateCreateRequest(ApprovalDecision.APPROVED, "IFRA 기준 충족"));

		verify(accessGuard).requireRole(10L, 1L, ProjectRole.SAFETY_REVIEWER);
		assertThat(response.decision()).isEqualTo(ApprovalDecision.APPROVED);
		assertThat(response.candidateId()).isEqualTo(500L);
	}

	@Test
	void register_is_forbidden_for_a_non_reviewer() {
		when(candidateService.getProjectId(500L, 1L)).thenReturn(10L);
		when(accessGuard.requireRole(10L, 1L, ProjectRole.SAFETY_REVIEWER))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.register(500L, 1L,
				new ApprovalGateCreateRequest(ApprovalDecision.APPROVED, "IFRA 기준 충족")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(repository, never()).save(any());
	}

	@Test
	void history_returns_most_recent_first() {
		when(repository.findByCandidateIdOrderByCreatedAtDesc(500L))
				.thenReturn(List.of(gate(2, ApprovalDecision.APPROVED), gate(1, ApprovalDecision.REJECTED)));

		List<ApprovalGateResponse> history = service.history(500L, 1L);

		verify(candidateService).assertAccessible(500L, 1L);
		assertThat(history).hasSize(2);
		assertThat(history.get(0).gateId()).isEqualTo(2L);
	}

	@Test
	void isApproved_reflects_latest_decision_only() {
		when(repository.findFirstByCandidateIdOrderByCreatedAtDesc(500L))
				.thenReturn(Optional.of(gate(2, ApprovalDecision.REJECTED)));
		assertThat(service.isApproved(500L)).isFalse();

		when(repository.findFirstByCandidateIdOrderByCreatedAtDesc(600L))
				.thenReturn(Optional.of(gate(3, ApprovalDecision.APPROVED)));
		assertThat(service.isApproved(600L)).isTrue();
	}

	@Test
	void isApproved_with_no_decisions_is_false() {
		when(repository.findFirstByCandidateIdOrderByCreatedAtDesc(700L)).thenReturn(Optional.empty());
		assertThat(service.isApproved(700L)).isFalse();
	}
}
