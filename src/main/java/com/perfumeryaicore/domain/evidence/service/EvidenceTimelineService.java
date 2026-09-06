package com.perfumeryaicore.domain.evidence.service;

import com.perfumeryaicore.domain.experiment.dto.response.ExperimentStatusLogResponse;
import com.perfumeryaicore.domain.experiment.service.ExperimentStatusService;
import com.perfumeryaicore.domain.evidence.dto.response.EvidenceEvent;
import com.perfumeryaicore.domain.formula.dto.response.CandidateVersionResponse;
import com.perfumeryaicore.domain.formula.service.CandidateService;
import com.perfumeryaicore.domain.safety.dto.response.ApprovalGateResponse;
import com.perfumeryaicore.domain.safety.service.ApprovalGateService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 후보의 감사 이력을 구성한다. 별도 로그 테이블을 쓰지 않고 formula(버전 생성)·safety(승인 게이트)·
 * experiment(상태 변경) 각자가 이미 갖고 있는 기록을 조회 시점에 모아 시간순으로 정렬한다.
 */
@Component
@RequiredArgsConstructor
public class EvidenceTimelineService {

	private final CandidateService candidateService;
	private final ApprovalGateService approvalGateService;
	private final ExperimentStatusService experimentStatusService;

	public List<EvidenceEvent> timeline(Long candidateId, Long memberId) {
		List<EvidenceEvent> events = new ArrayList<>();

		for (CandidateVersionResponse version : candidateService.versions(candidateId, memberId)) {
			String provider = version.generationMeta() == null ? null : version.generationMeta().provider();
			events.add(new EvidenceEvent(
					"CANDIDATE_VERSION_CREATED",
					version.versionId(),
					version.createdBy(),
					version.createdAt(),
					"버전 생성" + (provider != null ? " (AI: " + provider + ")" : "")));
		}

		for (ApprovalGateResponse gate : approvalGateService.history(candidateId, memberId)) {
			events.add(new EvidenceEvent(
					"APPROVAL_GATE_" + gate.decision(),
					null,
					gate.reviewedBy(),
					gate.reviewedAt(),
					gate.comment()));
		}

		for (ExperimentStatusLogResponse log : experimentStatusService.history(candidateId, memberId)) {
			events.add(new EvidenceEvent(
					"EXPERIMENT_STATUS_" + log.status(),
					null,
					log.changedBy(),
					log.changedAt(),
					null));
		}

		events.sort(Comparator.comparing(EvidenceEvent::occurredAt));
		return events;
	}
}
