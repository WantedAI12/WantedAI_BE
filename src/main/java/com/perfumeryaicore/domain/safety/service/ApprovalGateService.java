package com.perfumeryaicore.domain.safety.service;

import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.safety.dto.request.ApprovalGateCreateRequest;
import com.perfumeryaicore.domain.safety.dto.response.ApprovalGateResponse;
import com.perfumeryaicore.domain.safety.entity.ApprovalDecision;
import com.perfumeryaicore.domain.safety.entity.ApprovalGate;
import com.perfumeryaicore.domain.safety.repository.ApprovalGateRepository;
import com.perfumeryaicore.global.common.ProjectRole;
import java.util.List;
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
	private final ProjectAccessGuard accessGuard;

	@Transactional
	public ApprovalGateResponse register(Long candidateId, Long memberId, ApprovalGateCreateRequest dto) {
		Long projectId = candidateService.getProjectId(candidateId, memberId);
		accessGuard.requireRole(projectId, memberId, ProjectRole.SAFETY_REVIEWER);
		ApprovalGate gate = approvalGateRepository.save(
				ApprovalGate.register(candidateId, dto.decision(), dto.comment(), memberId));
		log.info("[SAFETY] approval-gate id={} candidate={} decision={} by={}",
				gate.getId(), candidateId, dto.decision(), memberId);
		return ApprovalGateResponse.from(gate);
	}

	public List<ApprovalGateResponse> history(Long candidateId, Long memberId) {
		candidateService.assertAccessible(candidateId, memberId);
		return approvalGateRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
				.map(ApprovalGateResponse::from)
				.toList();
	}

	/** 가장 최근 결정이 APPROVED인지. 결정 이력이 없으면 false(experiment 도메인에서 사용 예정). */
	public boolean isApproved(Long candidateId) {
		return approvalGateRepository.findFirstByCandidateIdOrderByCreatedAtDesc(candidateId)
				.map(gate -> gate.getDecision() == ApprovalDecision.APPROVED)
				.orElse(false);
	}
}
