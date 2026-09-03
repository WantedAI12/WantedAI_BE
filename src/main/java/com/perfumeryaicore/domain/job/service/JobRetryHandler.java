package com.perfumeryaicore.domain.job.service;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobType;

/**
 * 작업 종류별 재시도 처리기. 각 도메인(formula, ingredient 등)이 자신의 {@link JobType}에 대해
 * 구현하고 빈으로 등록하면 {@link JobService#retry}가 이를 찾아 원래 요청을 재구성해 다시 실행한다.
 *
 * <p>{@code job.getInputPayload()}에 enqueue 시점에 저장한 요청 JSON이 들어 있다.
 */
public interface JobRetryHandler {

	JobType supportedType();

	/**
	 * 실패한 작업을 다시 실행 큐에 넣는다. 이 시점의 {@code job} 상태는 이미 {@code PENDING}으로 초기화되어 있다.
	 */
	void redispatch(Job job);
}
