package com.perfumeryaicore.domain.safety.service;

import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.safety.dto.request.ApprovalGateCreateRequest;
import com.perfumeryaicore.domain.safety.dto.response.ApprovalGateResponse;
import com.perfumeryaicore.domain.safety.dto.response.SafetyEvaluationResponse;
import com.perfumeryaicore.domain.safety.entity.ApprovalDecision;
import com.perfumeryaicore.domain.safety.entity.ApprovalGate;
import com.perfumeryaicore.domain.safety.repository.ApprovalGateRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 안전·규제 승인 게이트 결정 등록·이력 조회.
 *
 * <p>승인/반려 결정은 대상 후보가 속한 프로젝트의 {@code SAFETY_REVIEWER}만 등록할 수 있다.
 * 이력 조회는 프로젝트 멤버라면 누구나 가능하다({@link CandidateService#assertAccessible}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalGateService {

	private final ApprovalGateRepository approvalGateRepository;
	private final CandidateService candidateService;
	private final SafetyEvaluationService safetyEvaluationService;
	private final ProjectAccessGuard accessGuard;

	@Transactional
	public ApprovalGateResponse register(Long candidateId, Long memberId, ApprovalGateCreateRequest dto) {
		Long projectId = candidateService.getProjectId(candidateId, memberId);
		accessGuard.requireRole(projectId, memberId, ProjectRole.SAFETY_REVIEWER);
		Long versionId = candidateService.getCurrentVersionId(candidateId, memberId);

		if (dto.decision() == ApprovalDecision.APPROVED) {
			requirePassingSafetyEvaluation(candidateId, memberId);
		}

		ApprovalGate gate = approvalGateRepository.save(
				ApprovalGate.register(candidateId, versionId, dto.decision(), dto.comment(), memberId));
		log.info("[SAFETY] approval-gate id={} candidate={} version={} decision={} by={}",
				gate.getId(), candidateId, versionId, dto.decision(), memberId);
		return ApprovalGateResponse.from(gate);
	}

	public List<ApprovalGateResponse> history(Long candidateId, Long memberId) {
		candidateService.assertAccessible(candidateId, memberId);
		return approvalGateRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
				.map(ApprovalGateResponse::from)
				.toList();
	}

	/**
	 * 가장 최근 결정이 APPROVED이고, 그 결정이 대상으로 한 버전이 지금 확인하려는 버전과 같은지(BE-032).
	 * 후보 버전이 승인 이후 바뀌었다면(재평가·복제 등) 과거 승인은 더 이상 유효하지 않다.
	 */
	public boolean isApprovedForVersion(Long candidateId, Long currentVersionId) {
		Optional<ApprovalGate> latest = approvalGateRepository.findFirstByCandidateIdOrderByCreatedAtDesc(candidateId);
		return latest.isPresent()
				&& latest.get().getDecision() == ApprovalDecision.APPROVED
				&& latest.get().getCandidateVersionId().equals(currentVersionId);
	}

	/**
	 * 필수 안전 게이트 강제(BE-031). 평가 자체가 없거나(§internal_gate_passed/§status 모두 null),
	 * 내부 게이트를 통과하지 못했거나, 위반 항목이 남아 있으면 승인을 거부한다.
	 */
	private void requirePassingSafetyEvaluation(Long candidateId, Long memberId) {
		SafetyEvaluationResponse safety = safetyEvaluationService.get(candidateId, memberId);
		boolean hasEvaluation = safety.status() != null || safety.internalGatePassed() != null;
		if (!hasEvaluation) {
			throw new BusinessException(ErrorCode.SAFETY_EVALUATION_MISSING);
		}
		boolean gatePassed = Boolean.TRUE.equals(safety.internalGatePassed());
		boolean hasViolations = safety.violations() != null && safety.violations().size() > 0;
		if (!gatePassed || hasViolations) {
			throw new BusinessException(ErrorCode.SAFETY_EVALUATION_NOT_PASSED);
		}
	}
}
