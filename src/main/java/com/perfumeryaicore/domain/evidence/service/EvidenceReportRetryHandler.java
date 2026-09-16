package com.perfumeryaicore.domain.evidence.service;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobRetryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code EVIDENCE_REPORT} 작업 재시도. {@code job.inputPayload}에 저장된 후보 ID로
 * 번들 생성을 다시 실행한다.
 */
@Component
@RequiredArgsConstructor
public class EvidenceReportRetryHandler implements JobRetryHandler {

	private final EvidenceReportService evidenceReportService;

	@Override
	public JobType supportedType() {
		return JobType.EVIDENCE_REPORT;
	}

	@Override
	public void redispatch(Job job) {
		Long candidateId = Long.valueOf(job.getInputPayload());
		evidenceReportService.dispatch(job.getId(), candidateId, job.getCreatedBy());
	}
}
