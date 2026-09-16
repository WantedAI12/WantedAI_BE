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
import com.perfumeryaicore.domain.safety.dto.response.SafetyEvaluationResponse;
import com.perfumeryaicore.domain.safety.entity.ApprovalDecision;
import com.perfumeryaicore.domain.safety.entity.ApprovalGate;
import com.perfumeryaicore.domain.safety.repository.ApprovalGateRepository;
import com.perfumeryaicore.domain.safety.service.ApprovalGateService;
import com.perfumeryaicore.domain.safety.service.SafetyEvaluationService;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

class ApprovalGateServiceTest {

	private static final long CANDIDATE_ID = 500L;
	private static final long VERSION_ID = 900L;

	private final ApprovalGateRepository repository = mock(ApprovalGateRepository.class);
	private final CandidateService candidateService = mock(CandidateService.class);
	private final SafetyEvaluationService safetyEvaluationService = mock(SafetyEvaluationService.class);
	private final ProjectAccessGuard accessGuard = mock(ProjectAccessGuard.class);
	private final ApprovalGateService service =
			new ApprovalGateService(repository, candidateService, safetyEvaluationService, accessGuard);
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private ApprovalGate gate(long id, long versionId, ApprovalDecision decision) {
		ApprovalGate gate = ApprovalGate.register(CANDIDATE_ID, versionId, decision, "comment", 1L);
		ReflectionTestUtils.setField(gate, "id", id);
		return gate;
	}

	private SafetyEvaluationResponse passingEvaluation() {
		ArrayNode empty = jsonMapper.createArrayNode();
		return new SafetyEvaluationResponse(CANDIDATE_ID, VERSION_ID, "PASSED", true, false, "internal",
				100.0, true, true, true, "EU", "eau_de_parfum", "audit-1", "2026-09-01", "2027-03-01",
				empty, empty, empty, empty);
	}

	private void actorIsReviewer() {
		when(candidateService.getProjectId(CANDIDATE_ID, 1L)).thenReturn(10L);
		when(accessGuard.requireRole(10L, 1L, ProjectRole.SAFETY_REVIEWER)).thenReturn(ProjectRole.SAFETY_REVIEWER);
		when(candidateService.getCurrentVersionId(CANDIDATE_ID, 1L)).thenReturn(VERSION_ID);
	}

	@Test
	void register_checks_reviewer_role_then_saves_an_approval_when_safety_passed() {
		actorIsReviewer();
		when(safetyEvaluationService.get(CANDIDATE_ID, 1L)).thenReturn(passingEvaluation());
		when(repository.save(any(ApprovalGate.class))).thenAnswer(inv -> {
			ApprovalGate g = inv.getArgument(0);
			ReflectionTestUtils.setField(g, "id", 1L);
			return g;
		});

		ApprovalGateResponse response = service.register(CANDIDATE_ID, 1L,
				new ApprovalGateCreateRequest(ApprovalDecision.APPROVED, "IFRA 기준 충족"));

		verify(accessGuard).requireRole(10L, 1L, ProjectRole.SAFETY_REVIEWER);
		assertThat(response.decision()).isEqualTo(ApprovalDecision.APPROVED);
		assertThat(response.candidateId()).isEqualTo(CANDIDATE_ID);
		assertThat(response.candidateVersionId()).isEqualTo(VERSION_ID);
	}

	@Test
	void register_is_forbidden_for_a_non_reviewer() {
		when(candidateService.getProjectId(CANDIDATE_ID, 1L)).thenReturn(10L);
		when(accessGuard.requireRole(10L, 1L, ProjectRole.SAFETY_REVIEWER))
				.thenThrow(new BusinessException(ErrorCode.PROJECT_ROLE_FORBIDDEN));

		assertThatThrownBy(() -> service.register(CANDIDATE_ID, 1L,
				new ApprovalGateCreateRequest(ApprovalDecision.APPROVED, "IFRA 기준 충족")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.PROJECT_ROLE_FORBIDDEN);
		verify(repository, never()).save(any());
	}

	/** BE-031: 안전 평가 자체가 없으면(status/internalGatePassed 모두 null) 승인을 거부한다. */
	@Test
	void register_rejects_approval_when_no_safety_evaluation_exists() {
		actorIsReviewer();
		when(safetyEvaluationService.get(CANDIDATE_ID, 1L)).thenReturn(new SafetyEvaluationResponse(
				CANDIDATE_ID, VERSION_ID, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null));

		assertThatThrownBy(() -> service.register(CANDIDATE_ID, 1L,
				new ApprovalGateCreateRequest(ApprovalDecision.APPROVED, "충족")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SAFETY_EVALUATION_MISSING);
		verify(repository, never()).save(any());
	}

	/** BE-031: 내부 게이트를 통과하지 못했으면 승인을 거부한다. */
	@Test
	void register_rejects_approval_when_the_internal_gate_did_not_pass() {
		actorIsReviewer();
		ArrayNode empty = jsonMapper.createArrayNode();
		when(safetyEvaluationService.get(CANDIDATE_ID, 1L)).thenReturn(new SafetyEvaluationResponse(
				CANDIDATE_ID, VERSION_ID, "FAILED", false, false, "internal", 40.0, false, false, false,
				"EU", "eau_de_parfum", "audit-1", "2026-09-01", "2027-03-01", empty, empty, empty, empty));

		assertThatThrownBy(() -> service.register(CANDIDATE_ID, 1L,
				new ApprovalGateCreateRequest(ApprovalDecision.APPROVED, "충족")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SAFETY_EVALUATION_NOT_PASSED);
		verify(repository, never()).save(any());
	}

	/** BE-031: 게이트는 통과했어도 위반 항목이 남아 있으면 승인을 거부한다. */
	@Test
	void register_rejects_approval_when_violations_remain() {
		actorIsReviewer();
		ArrayNode violations = jsonMapper.createArrayNode();
		violations.add("banned_substance_x");
		ArrayNode empty = jsonMapper.createArrayNode();
		when(safetyEvaluationService.get(CANDIDATE_ID, 1L)).thenReturn(new SafetyEvaluationResponse(
				CANDIDATE_ID, VERSION_ID, "PASSED", true, false, "internal", 90.0, true, true, true,
				"EU", "eau_de_parfum", "audit-1", "2026-09-01", "2027-03-01", violations, empty, empty, empty));

		assertThatThrownBy(() -> service.register(CANDIDATE_ID, 1L,
				new ApprovalGateCreateRequest(ApprovalDecision.APPROVED, "충족")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SAFETY_EVALUATION_NOT_PASSED);
		verify(repository, never()).save(any());
	}

	/** BE-031: 반려는 안전 평가 상태와 무관하게 항상 저장할 수 있다. */
	@Test
	void register_allows_rejection_regardless_of_safety_evaluation() {
		actorIsReviewer();
		when(repository.save(any(ApprovalGate.class))).thenAnswer(inv -> {
			ApprovalGate g = inv.getArgument(0);
			ReflectionTestUtils.setField(g, "id", 1L);
			return g;
		});

		ApprovalGateResponse response = service.register(CANDIDATE_ID, 1L,
				new ApprovalGateCreateRequest(ApprovalDecision.REJECTED, "재작업 필요"));

		assertThat(response.decision()).isEqualTo(ApprovalDecision.REJECTED);
		verify(safetyEvaluationService, never()).get(any(), any());
	}

	@Test
	void history_returns_most_recent_first() {
		when(repository.findByCandidateIdOrderByCreatedAtDesc(CANDIDATE_ID))
				.thenReturn(List.of(gate(2, VERSION_ID, ApprovalDecision.APPROVED), gate(1, VERSION_ID, ApprovalDecision.REJECTED)));

		List<ApprovalGateResponse> history = service.history(CANDIDATE_ID, 1L);

		verify(candidateService).assertAccessible(CANDIDATE_ID, 1L);
		assertThat(history).hasSize(2);
		assertThat(history.get(0).gateId()).isEqualTo(2L);
	}

	@Test
	void isApprovedForVersion_is_true_only_when_the_latest_approval_targets_that_version() {
		when(repository.findFirstByCandidateIdOrderByCreatedAtDesc(CANDIDATE_ID))
				.thenReturn(Optional.of(gate(2, VERSION_ID, ApprovalDecision.REJECTED)));
		assertThat(service.isApprovedForVersion(CANDIDATE_ID, VERSION_ID)).isFalse();

		when(repository.findFirstByCandidateIdOrderByCreatedAtDesc(600L))
				.thenReturn(Optional.of(gate(3, VERSION_ID, ApprovalDecision.APPROVED)));
		assertThat(service.isApprovedForVersion(600L, VERSION_ID)).isTrue();

		// BE-032: 승인은 VERSION_ID를 대상으로 했는데 지금 확인하는 버전이 다르면(새 버전 생김) 무효.
		assertThat(service.isApprovedForVersion(600L, 901L)).isFalse();
	}

	@Test
	void isApprovedForVersion_with_no_decisions_is_false() {
		when(repository.findFirstByCandidateIdOrderByCreatedAtDesc(700L)).thenReturn(Optional.empty());
		assertThat(service.isApprovedForVersion(700L, VERSION_ID)).isFalse();
	}
}
