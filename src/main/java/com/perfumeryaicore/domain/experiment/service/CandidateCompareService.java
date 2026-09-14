package com.perfumeryaicore.domain.experiment.service;

import com.perfumeryaicore.domain.experiment.dto.response.CandidateCompareRow;
import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse.IngredientLine;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import java.util.DoubleSummaryStatistics;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 후보 비교표. formula(현재 버전·비용·원료 가용성)와 prediction(유사도·적용범위)을
 * 조회 시점에 모아 보여준다 — 별도로 저장하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CandidateCompareService {

	/** 한 번에 비교할 수 있는 후보 수 상한(BE-084). FE 화면 기본값(3)과는 별개인 백엔드 안전장치 —
	 * 정확한 값은 팀 확정 전이라 잠정값이다. */
	private static final int MAX_COMPARE_CANDIDATES = 10;

	private final CandidateService candidateService;
	private final PredictionService predictionService;

	public List<CandidateCompareRow> compare(Long requestId, List<Long> candidateIds, Long memberId) {
		validateIds(candidateIds);
		return candidateIds.stream()
				.map(candidateId -> compareOne(requestId, candidateId, memberId))
				.toList();
	}

	private void validateIds(List<Long> candidateIds) {
		if (candidateIds == null || candidateIds.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "비교할 후보 ID가 최소 1개 필요합니다.");
		}
		if (candidateIds.size() > MAX_COMPARE_CANDIDATES) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED,
					"한 번에 비교할 수 있는 후보는 최대 " + MAX_COMPARE_CANDIDATES + "개입니다.");
		}
		Set<Long> distinct = new HashSet<>(candidateIds);
		if (distinct.size() != candidateIds.size()) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "중복된 후보 ID가 있습니다.");
		}
	}

	private CandidateCompareRow compareOne(Long requestId, Long candidateId, Long memberId) {
		CandidateResponse candidate = candidateService.get(candidateId, memberId);
		if (!candidate.requestId().equals(requestId)) {
			throw new BusinessException(ErrorCode.CANDIDATE_NOT_FOUND,
					"해당 요청에 속한 후보가 아닙니다: " + candidateId);
		}
		PredictionResponse prediction = predictionService.get(candidateId, memberId);

		return new CandidateCompareRow(
				candidateId,
				candidate.status(),
				prediction.similarityScore(),
				candidate.currentVersion() == null ? null : candidate.currentVersion().cost(),
				averageAvailabilityPercent(candidate),
				prediction.modelApplicabilityPercent());
	}

	private Double averageAvailabilityPercent(CandidateResponse candidate) {
		if (candidate.currentVersion() == null || candidate.currentVersion().ingredients() == null) {
			return null;
		}
		DoubleSummaryStatistics stats = candidate.currentVersion().ingredients().stream()
				.map(IngredientLine::availability)
				.filter(Objects::nonNull)
				.mapToDouble(Double::doubleValue)
				.summaryStatistics();
		return stats.getCount() == 0 ? null : stats.getAverage() * 100;
	}
}
