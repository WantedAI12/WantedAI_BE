package com.perfumeryaicore.domain.lotion.service;

import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.formula.service.CandidateVersionRawView;
import com.perfumeryaicore.domain.lotion.dto.response.LotionDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 바디로션 후보의 성능 프록시 상세 조회. 새 AI 호출을 하지 않는다(§0.5).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LotionDetailService {

	private final CandidateService candidateService;
	private final LotionDetailMapper mapper;

	public LotionDetailResponse get(Long candidateId, Long memberId) {
		CandidateVersionRawView raw = candidateService.getCurrentVersionRaw(candidateId, memberId);
		return mapper.toResponse(raw);
	}
}
