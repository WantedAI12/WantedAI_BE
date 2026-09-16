package com.perfumeryaicore.domain.evidence.service;

import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.domain.safety.service.SafetyEvaluationService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 증거 보고서용 스냅샷을 한 트랜잭션 안에서 모은다(BE-058).
 *
 * <p>{@link EvidenceReportService#generate}가 후보·안전·예측·감사·관능을 각각 독립 호출로
 * 조회하면, 그 사이에 다른 사용자가 후보 버전을 바꾸거나 승인/공급 재평가를 마치는 경우
 * PDF와 JSON이 서로 다른 시점의 값을 섞어 담을 수 있었다. 이 메서드 전체를 하나의 읽기 전용
 * 트랜잭션으로 묶으면 MySQL REPEATABLE READ(기본 격리수준)가 트랜잭션 시작 시점의 스냅샷을
 * 트랜잭션이 끝날 때까지 그대로 보여주므로, 5개 조회가 모두 같은 시점의 데이터를 본다.
 *
 * <p>별도 빈으로 분리한 이유: {@code @Transactional}은 스프링 프록시를 거쳐야 적용되는데,
 * 같은 클래스 안에서 자기 자신의 메서드를 직접 호출(self-invocation)하면 프록시를 우회해
 * 트랜잭션이 걸리지 않는다. {@link EvidenceReportService#dispatch}가
 * {@link EvidenceReportService#generate}를 내부에서 직접 부르는 구조라, 스냅샷 수집을
 * 별도 빈의 공개 메서드로 빼야 실제로 트랜잭션이 걸린다.
 */
@Service
@RequiredArgsConstructor
public class EvidenceReportSnapshotService {

	private final CandidateService candidateService;
	private final SafetyEvaluationService safetyEvaluationService;
	private final PredictionService predictionService;
	private final EvidenceTimelineService evidenceTimelineService;
	private final SensoryTestService sensoryTestService;

	@Transactional(readOnly = true)
	public EvidenceReportBundle snapshot(Long candidateId, Long memberId) {
		return new EvidenceReportBundle(
				candidateId,
				candidateService.get(candidateId, memberId),
				safetyEvaluationService.get(candidateId, memberId),
				predictionService.get(candidateId, memberId),
				evidenceTimelineService.timeline(candidateId, memberId),
				sensoryTestService.list(candidateId, memberId),
				LocalDateTime.now(),
				memberId);
	}
}
