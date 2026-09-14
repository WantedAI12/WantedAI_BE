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
import jakarta.persistence.Version;
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

	/**
	 * 이 작업의 실행 세대(generation) 번호. PENDING → RUNNING 전이마다 1씩 증가한다.
	 * 재시도로 새 시도가 시작된 뒤 이전 시도의 비동기 응답이 뒤늦게 도착해도, 호출자가 들고 있던
	 * attempt 값과 현재 값이 달라지므로 {@link com.perfumeryaicore.domain.job.service.JobService}가
	 * 그 응답을 무시할 수 있다.
	 */
	@Column(nullable = false)
	private int attempt;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	/**
	 * 재시도 시 원래 요청을 재구성하기 위한 입력값. 형식은 {@link JobType}별 {@code JobRetryHandler}
	 * 구현체가 정한다(꼭 JSON일 필요는 없음). 도메인이 enqueue 시점에 채운다.
	 */
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

	/**
	 * 낙관적 잠금(BE-043). 워커 두 개가 같은 PENDING 작업을 동시에 선점하거나, 재시도·취소·완료가
	 * 서로 경합하면 먼저 커밋한 쪽만 성공하고 나머지는
	 * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}로 실패한다.
	 */
	@Version
	@Column(nullable = false)
	private Long version;

	private Job(Long projectId, JobType jobType, Long createdBy, String inputPayload) {
		this.projectId = projectId;
		this.jobType = jobType;
		this.createdBy = createdBy;
		this.inputPayload = inputPayload;
		this.status = JobStatus.PENDING;
		this.retryable = false;
		this.attempt = 0;
	}

	public static Job pending(Long projectId, JobType jobType, Long createdBy, String inputPayload) {
		return new Job(projectId, jobType, createdBy, inputPayload);
	}

	// --- 상태 전이 ---

	/**
	 * 비동기 워커가 작업을 집어 AI 호출 대기열에 넣었다. 새 시도이므로 attempt를 증가시킨다.
	 *
	 * @return 이번에 시작된 시도의 attempt 번호
	 */
	public int markRunning() {
		requireStatus(JobStatus.PENDING);
		this.status = JobStatus.RUNNING;
		this.aiCallQueuedAt = LocalDateTime.now();
		this.attempt++;
		return this.attempt;
	}

	/** 직렬화 게이트를 통과해 실제 AI 호출을 시작했다. {@code attempt}가 현재 시도와 다르면 무시한다. */
	public void markAiCallStarted(int attempt) {
		if (!isCurrentAttempt(attempt)) {
			return;
		}
		this.aiCallStartedAt = LocalDateTime.now();
	}

	/**
	 * {@code attempt}가 현재 시도와 다르거나(이전 시도의 지연 응답) 이미 RUNNING이 아니면(취소됨 등)
	 * 조용히 무시한다 — 늦게 도착한 응답이 취소·재시도로 이미 지나간 상태를 덮어쓰지 않게 한다.
	 */
	public void markSucceeded(int attempt, Long resultRefId) {
		if (!isRunningAttempt(attempt)) {
			return;
		}
		this.status = JobStatus.SUCCEEDED;
		this.resultRefId = resultRefId;
		this.failureReason = null;
		this.retryable = false;
	}

	/**
	 * {@code attempt}가 현재 시도와 다르거나(이전 시도의 지연 응답) 이미 RUNNING이 아니면(취소됨 등)
	 * 조용히 무시한다 — 늦게 도착한 응답이 취소·재시도로 이미 지나간 상태를 덮어쓰지 않게 한다.
	 */
	public void markFailed(int attempt, String reason, boolean retryable) {
		if (!isRunningAttempt(attempt)) {
			return;
		}
		this.status = JobStatus.FAILED;
		this.failureReason = truncate(reason);
		this.retryable = retryable;
	}

	/** 주어진 attempt 번호가 이 작업의 현재(가장 최근) 시도인지. */
	public boolean isCurrentAttempt(int attempt) {
		return this.attempt == attempt;
	}

	/** 주어진 attempt가 현재 시도이면서 아직 RUNNING인지 — 결과를 커밋해도 되는 유일한 상태. */
	public boolean isRunningAttempt(int attempt) {
		return status == JobStatus.RUNNING && isCurrentAttempt(attempt);
	}

	public void cancel() {
		if (!status.isCancellable()) {
			throw new BusinessException(ErrorCode.JOB_NOT_CANCELLABLE);
		}
		this.status = JobStatus.CANCELLED;
	}

	/**
	 * 프로세스가 재시작됐는데 RUNNING으로 남아있던 작업을 강제로 실패 처리한다(BE-044).
	 * 그 실행은 이미 죽은 프로세스의 것이므로 attempt 일치 여부와 무관하게 무조건 전환하고,
	 * 재시도 가능으로 표시해 다음 기동 복구가 다시 집어갈 수 있게 한다. RUNNING이 아니면
	 * (이미 완료·취소됨) 조용히 무시한다.
	 */
	public void markOrphaned(String reason) {
		if (status != JobStatus.RUNNING) {
			return;
		}
		this.status = JobStatus.FAILED;
		this.failureReason = truncate(reason);
		this.retryable = true;
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
