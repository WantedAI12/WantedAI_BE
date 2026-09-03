package com.perfumeryaicore.domain.job.entity;

import com.perfumeryaicore.global.common.BaseTimeEntity;
import com.perfumeryaicore.global.exception.BusinessException;
import com.perfumeryaicore.global.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 외부 AI 서비스 호출을 감싸는 비동기 작업. 트리거 API는 즉시 이 작업의 식별자를 반환하고,
 * 클라이언트는 {@code GET /jobs/{jobId}} 폴링으로 진행 상태를 확인한다.
 *
 * <p>상태 전이는 이 엔티티의 메서드로만 수행한다. 잘못된 전이는 {@link BusinessException}으로 막는다.
 */
@Entity
@Getter
@Table(
		name = "jobs",
		indexes = {
				@Index(name = "idx_jobs_project_id", columnList = "project_id"),
				@Index(name = "idx_jobs_status", columnList = "status")
		}
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_type", nullable = false, length = 40)
	private JobType jobType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private JobStatus status;

	@Column(nullable = false)
	private boolean retryable;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	/** 재시도 시 원래 요청을 재구성하기 위한 입력 payload(JSON). 도메인이 enqueue 시점에 채운다. */
	@Lob
	@Column(name = "input_payload")
	private String inputPayload;

	/** 작업 성공 시 생성된 도메인 리소스 식별자(후보 ID, 요청 ID 등). */
	@Column(name = "result_ref_id")
	private Long resultRefId;

	@Column(name = "ai_call_queued_at")
	private LocalDateTime aiCallQueuedAt;

	@Column(name = "ai_call_started_at")
	private LocalDateTime aiCallStartedAt;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	private Job(Long projectId, JobType jobType, Long createdBy, String inputPayload) {
		this.projectId = projectId;
		this.jobType = jobType;
		this.createdBy = createdBy;
		this.inputPayload = inputPayload;
		this.status = JobStatus.PENDING;
		this.retryable = false;
	}

	public static Job pending(Long projectId, JobType jobType, Long createdBy, String inputPayload) {
		return new Job(projectId, jobType, createdBy, inputPayload);
	}

	// --- 상태 전이 ---

	/** 비동기 워커가 작업을 집어 AI 호출 대기열에 넣었다. */
	public void markRunning() {
		requireStatus(JobStatus.PENDING);
		this.status = JobStatus.RUNNING;
		this.aiCallQueuedAt = LocalDateTime.now();
	}

	/** 직렬화 게이트를 통과해 실제 AI 호출을 시작했다. */
	public void markAiCallStarted() {
		this.aiCallStartedAt = LocalDateTime.now();
	}

	public void markSucceeded(Long resultRefId) {
		requireStatus(JobStatus.RUNNING);
		this.status = JobStatus.SUCCEEDED;
		this.resultRefId = resultRefId;
		this.failureReason = null;
		this.retryable = false;
	}

	public void markFailed(String reason, boolean retryable) {
		requireStatus(JobStatus.RUNNING);
		this.status = JobStatus.FAILED;
		this.failureReason = truncate(reason);
		this.retryable = retryable;
	}

	public void cancel() {
		if (!status.isCancellable()) {
			throw new BusinessException(ErrorCode.JOB_NOT_CANCELLABLE);
		}
		this.status = JobStatus.CANCELLED;
	}

	/** 실패한 재시도 가능 작업을 다시 대기 상태로 되돌린다. */
	public void resetForRetry() {
		if (status != JobStatus.FAILED || !retryable) {
			throw new BusinessException(ErrorCode.JOB_NOT_RETRYABLE);
		}
		this.status = JobStatus.PENDING;
		this.failureReason = null;
		this.retryable = false;
		this.aiCallQueuedAt = null;
		this.aiCallStartedAt = null;
	}

	public boolean isOwnedBy(Long memberId) {
		return createdBy.equals(memberId);
	}

	private void requireStatus(JobStatus expected) {
		if (status != expected) {
			throw new BusinessException(ErrorCode.JOB_ILLEGAL_STATE,
					"작업 상태 전이가 올바르지 않습니다. 현재=" + status + ", 기대=" + expected);
		}
	}

	private static String truncate(String reason) {
		if (reason == null) {
			return null;
		}
		return reason.length() <= 500 ? reason : reason.substring(0, 500);
	}
}
