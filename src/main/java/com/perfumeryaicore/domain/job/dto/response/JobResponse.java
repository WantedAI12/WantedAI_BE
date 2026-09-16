package com.perfumeryaicore.domain.job.dto.response;

import com.perfumeryaicore.domain.job.entity.Job;
import com.perfumeryaicore.domain.job.entity.JobStatus;
import com.perfumeryaicore.domain.job.entity.JobType;
import java.time.LocalDateTime;

/**
 * 작업 상태 조회 응답. 인증 정보나 내부 요청 헤더는 포함하지 않는다.
 *
 * @param resultRefId 성공 시 조회할 도메인 리소스 식별자 (후보 ID 등). 미완료면 {@code null}
 */
public record JobResponse(
		Long jobId,
		JobType jobType,
		JobStatus status,
		boolean retryable,
		String failureReason,
		Long resultRefId,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static JobResponse from(Job job) {
		return new JobResponse(
				job.getId(),
				job.getJobType(),
				job.getStatus(),
				job.isRetryable(),
				job.getFailureReason(),
				job.getResultRefId(),
				job.getCreatedAt(),
				job.getUpdatedAt());
	}
}
