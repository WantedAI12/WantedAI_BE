package com.perfumeryaicore.domain.formula.service;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobType;
import com.perfumeryaicore.domain.job.service.JobRetryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code CANDIDATE_GENERATION} 작업 재시도. {@code job.inputPayload}에 저장된 요청 ID로
 * 원래 생성 흐름을 다시 실행한다.
 */
@Component
@RequiredArgsConstructor
public class CandidateGenerationRetryHandler implements JobRetryHandler {

	private final CandidateGenerationService generationService;

	@Override
	public JobType supportedType() {
		return JobType.CANDIDATE_GENERATION;
	}

	@Override
	public void redispatch(Job job) {
		Long requestId = Long.valueOf(job.getInputPayload());
		generationService.dispatch(job.getId(), requestId, job.getCreatedBy());
	}
}
