package com.perfumeryaicore.domain.prediction.service;

import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.formula.service.CandidateVersionRawView;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionUncertaintyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후보의 성능 프록시 예측 조회. 새 AI 호출을 하지 않는다(§0.5) — 재계산이 실제로 필요하면
 * 후보를 다시 생성해야 하며, 그 트리거는 아직 배포되지 않았다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PredictionService {

	private final CandidateService candidateService;
	private final PredictionMapper mapper;

	public PredictionResponse get(Long candidateId, Long memberId) {
		CandidateVersionRawView raw = candidateService.getCurrentVersionRaw(candidateId, memberId);
		return mapper.toResponse(raw);
	}

	public PredictionUncertaintyResponse getUncertainty(Long candidateId, Long memberId) {
		CandidateVersionRawView raw = candidateService.getCurrentVersionRaw(candidateId, memberId);
		return mapper.toUncertaintyResponse(raw);
	}
}
