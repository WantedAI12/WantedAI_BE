package com.perfumeryaicore.domain.evidence.service;

import com.perfumeryaicore.domain.evidence.dto.response.EvidenceEvent;
import com.perfumeryaicore.domain.evidence.dto.response.SensoryTestResponse;
import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.safety.dto.response.SafetyEvaluationResponse;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 증거 보고서의 실제 내용. 후보·현재 버전·안전 평가·예측·감사 이력·관능 검증을 한 시점에
 * 모은 스냅샷이며, 이 자체가 JSON으로 직렬화되어 {@code evidence_reports.report_data}에 저장된다.
 */
public record EvidenceReportBundle(
		Long candidateId,
		CandidateResponse candidate,
		SafetyEvaluationResponse safety,
		PredictionResponse prediction,
		List<EvidenceEvent> timeline,
		List<SensoryTestResponse> sensoryTests,
		LocalDateTime generatedAt,
		Long generatedBy
) {
}
