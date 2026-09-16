package com.perfumeryaicore.domain.experiment.service;

import com.perfumeryaicore.domain.experiment.dto.response.ExperimentStatusLogResponse;
import com.perfumeryaicore.domain.experiment.entity.ExperimentStatusLog;
import com.perfumeryaicore.domain.experiment.repository.ExperimentStatusLogRepository;
import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.project.service.ProjectAccessGuard;
import com.perfumeryaicore.domain.safety.service.ApprovalGateService;
import com.perfumeryaicore.global.common.CandidateStatus;
import com.perfumeryaicore.global.common.ProjectRole;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후보를 실험 후보로 확정하거나 실험 상태를 바꾼다. 상태 순서 검증은 formula 도메인의
 * {@code Candidate}가 맡고, 여기서는 도메인을 가로지르는 규칙(안전 게이트 승인)만 확인한다.
 *
 * <p>BE-103: CONFIRMED_FOR_EXPERIMENT 상태의 후보는 UNDER_REVIEW로 선택 해제할 수 있고,
 * 이후 다시 확정(재선택)하면 안전 게이트를 다시 확인한다 — 별도 API 없이 이 메서드에
 * target=UNDER_REVIEW/CONFIRMED_FOR_EXPERIMENT를 반복 호출하는 것으로 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExperimentStatusService {

	/** 실험 확정·상태 전이는 이 세 역할만 수행한다(BE-004). */
	private static final ProjectRole[] TRANSITION_ROLES = {
			ProjectRole.PERFUMER, ProjectRole.FRAGRANCE_RND, ProjectRole.PROJECT_MANAGER
	};

	private final CandidateService candidateService;
	private final ApprovalGateService approvalGateService;
	private final ExperimentStatusLogRepository logRepository;
	private final ProjectAccessGuard accessGuard;

	@Transactional
	public ExperimentStatusLogResponse changeStatus(Long candidateId, Long memberId, CandidateStatus target,
			String reason) {
		Long projectId = candidateService.getProjectId(candidateId, memberId);
		accessGuard.requireRole(projectId, memberId, TRANSITION_ROLES);

		// BE-040: 전이 직전 상태·현재 버전을 먼저 읽어 이력에 고정한다.
		CandidateResponse candidate = candidateService.get(candidateId, memberId);
		CandidateStatus previousStatus = candidate.status();
		Long candidateVersionId = candidateService.getCurrentVersionId(candidateId, memberId);

		if (target == CandidateStatus.CONFIRMED_FOR_EXPERIMENT
				&& !approvalGateService.isApprovedForVersion(candidateId, candidateVersionId)) {
			throw new BusinessException(ErrorCode.SAFETY_GATE_NOT_APPROVED);
		}

		candidateService.transitionStatus(candidateId, memberId, target);
		ExperimentStatusLog logEntry = logRepository.save(ExperimentStatusLog.record(
				candidateId, previousStatus, target, candidateVersionId, reason, memberId));
		log.info("[EXPERIMENT] candidate={} status={} -> {} by={}", candidateId, previousStatus, target, memberId);
		return ExperimentStatusLogResponse.from(logEntry);
	}

	public List<ExperimentStatusLogResponse> history(Long candidateId, Long memberId) {
		candidateService.assertAccessible(candidateId, memberId);
		return logRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
				.map(ExperimentStatusLogResponse::from)
				.toList();
	}
}
