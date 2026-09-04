package com.perfumeryaicore.domain.safety.service;

import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.safety.dto.request.ApprovalGateCreateRequest;
import com.perfumeryaicore.domain.safety.dto.response.ApprovalGateResponse;
import com.perfumeryaicore.domain.safety.entity.ApprovalDecision;
import com.perfumeryaicore.domain.safety.entity.ApprovalGate;
import com.perfumeryaicore.domain.safety.repository.ApprovalGateRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 안전·규제 승인 게이트 결정 등록·이력 조회.
 *
 * <p>승인 권한(SAFETY_REVIEWER)은 project 도메인의 역할 배정이 아직 없어 여기서는
 * 로그인 여부만 확인한다. TODO(project): SAFETY_REVIEWER 역할 검증 추가.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalGateService {

	private final ApprovalGateRepository approvalGateRepository;
	private final CandidateService candidateService;

	@Transactional
	public ApprovalGateResponse register(Long candidateId, Long memberId, ApprovalGateCreateRequest dto) {
		candidateService.assertAccessible(candidateId, memberId);
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
