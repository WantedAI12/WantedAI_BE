package com.perfumeryaicore.domain.safety.service;

import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.formula.service.CandidateVersionRawView;
import com.perfumeryaicore.domain.safety.dto.response.SafetyEvaluationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후보의 안전·규제·공급 적합성 평가 조회. 새 AI 호출을 하지 않는다(§0.5) —
 * 재평가가 실제로 필요하면 후보를 다시 생성해야 하며, 그 트리거는 아직 배포되지 않았다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SafetyEvaluationService {

	private final CandidateService candidateService;
	private final SafetyEvaluationMapper mapper;

	public SafetyEvaluationResponse get(Long candidateId, Long memberId) {
		CandidateVersionRawView raw = candidateService.getCurrentVersionRaw(candidateId, memberId);
		return mapper.toResponse(raw);
	}
}
