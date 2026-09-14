package com.perfumeryaicore.domain.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.perfumeryaicore.domain.evidence.service.EvidenceReportBundle;
import com.perfumeryaicore.domain.evidence.service.EvidenceReportSnapshotService;
import com.perfumeryaicore.domain.evidence.service.EvidenceTimelineService;
import com.perfumeryaicore.domain.evidence.service.SensoryTestService;
import com.perfumeryaicore.domain.formula.dto.response.CandidateResponse;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.prediction.dto.response.PredictionResponse;
import com.perfumeryaicore.domain.prediction.service.PredictionService;
import com.perfumeryaicore.domain.safety.dto.response.SafetyEvaluationResponse;
import com.perfumeryaicore.domain.safety.service.SafetyEvaluationService;
import com.perfumeryaicore.global.common.CandidateStatus;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * BE-058: 스냅샷 수집이 실제로 하나의 트랜잭션으로 묶여야 한다는 계약을 검증한다. 순수 mock
 * 단위 테스트로는 MySQL REPEATABLE READ의 일관성 자체를 재현할 수 없으므로, (1) 5개 조회
 * 결과가 한 번의 호출로 정확히 조합되는지, (2) 트랜잭션 경계가 실제로 선언돼 있는지(스프링
 * 프록시가 self-invocation을 우회하지 못하도록 별도 빈으로 분리한 이유) 두 가지를 확인한다.
 */
class EvidenceReportSnapshotServiceTest {

	private final CandidateService candidateService = mock(CandidateService.class);
	private final SafetyEvaluationService safetyEvaluationService = mock(SafetyEvaluationService.class);
	private final PredictionService predictionService = mock(PredictionService.class);
	private final EvidenceTimelineService evidenceTimelineService = mock(EvidenceTimelineService.class);
	private final SensoryTestService sensoryTestService = mock(SensoryTestService.class);

	private final EvidenceReportSnapshotService service = new EvidenceReportSnapshotService(
			candidateService, safetyEvaluationService, predictionService,
			evidenceTimelineService, sensoryTestService);

	@Test
	void snapshot_combines_all_five_sources_into_one_bundle() {
		CandidateResponse candidate = new CandidateResponse(900L, 5L, CandidateStatus.UNDER_REVIEW, null, null, null, null);
		SafetyEvaluationResponse safety = mock(SafetyEvaluationResponse.class);
		PredictionResponse prediction = mock(PredictionResponse.class);
		when(candidateService.get(900L, 1L)).thenReturn(candidate);
		when(safetyEvaluationService.get(900L, 1L)).thenReturn(safety);
		when(predictionService.get(900L, 1L)).thenReturn(prediction);
		when(evidenceTimelineService.timeline(900L, 1L)).thenReturn(List.of());
		when(sensoryTestService.list(900L, 1L)).thenReturn(List.of());

		EvidenceReportBundle bundle = service.snapshot(900L, 1L);

		assertThat(bundle.candidateId()).isEqualTo(900L);
		assertThat(bundle.candidate()).isEqualTo(candidate);
		assertThat(bundle.safety()).isEqualTo(safety);
		assertThat(bundle.prediction()).isEqualTo(prediction);
		assertThat(bundle.generatedBy()).isEqualTo(1L);
		assertThat(bundle.generatedAt()).isNotNull();
	}

	/**
	 * 이 메서드가 별도 빈의 public 메서드이자 @Transactional(readOnly=true)로 선언돼 있어야
	 * self-invocation 문제 없이 스프링 프록시가 실제로 트랜잭션을 걸 수 있다.
	 */
	@Test
	void snapshot_is_transactional_so_all_five_reads_share_one_connection_level_snapshot() throws NoSuchMethodException {
		Method method = EvidenceReportSnapshotService.class.getMethod("snapshot", Long.class, Long.class);

		Transactional annotation = method.getAnnotation(Transactional.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.readOnly()).isTrue();
	}
}
