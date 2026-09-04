package com.perfumeryaicore.domain.experiment.service;

import com.perfumeryaicore.domain.experiment.dto.response.ExperimentStatusLogResponse;
import com.perfumeryaicore.domain.experiment.entity.ExperimentStatusLog;
import com.perfumeryaicore.domain.experiment.repository.ExperimentStatusLogRepository;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.safety.service.ApprovalGateService;
import com.perfumeryaicore.global.common.CandidateStatus;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExperimentStatusService {

	private final CandidateService candidateService;
	private final ApprovalGateService approvalGateService;
	private final ExperimentStatusLogRepository logRepository;

	@Transactional
	public ExperimentStatusLogResponse changeStatus(Long candidateId, Long memberId, CandidateStatus target) {
		if (target == CandidateStatus.CONFIRMED_FOR_EXPERIMENT && !approvalGateService.isApproved(candidateId)) {
			throw new BusinessException(ErrorCode.SAFETY_GATE_NOT_APPROVED);
		}

		candidateService.transitionStatus(candidateId, memberId, target);
		ExperimentStatusLog logEntry = logRepository.save(
				ExperimentStatusLog.record(candidateId, target, memberId));
		log.info("[EXPERIMENT] candidate={} status={} by={}", candidateId, target, memberId);
		return ExperimentStatusLogResponse.from(logEntry);
	}

	public List<ExperimentStatusLogResponse> history(Long candidateId, Long memberId) {
		candidateService.assertAccessible(candidateId, memberId);
		return logRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId).stream()
				.map(ExperimentStatusLogResponse::from)
				.toList();
	}
}
