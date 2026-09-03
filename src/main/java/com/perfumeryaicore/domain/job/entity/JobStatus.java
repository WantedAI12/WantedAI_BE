package com.perfumeryaicore.domain.job.entity;

/**
 * 비동기 작업 상태.
 *
 * <pre>
 *   PENDING ──▶ RUNNING ──▶ SUCCEEDED
 *      │           │
 *      │           └──▶ FAILED ──▶ (retryable 이면) PENDING
 *      └──────────────▶ CANCELLED ◀── RUNNING
 * </pre>
 */
public enum JobStatus {

	PENDING,
	RUNNING,
	SUCCEEDED,
	FAILED,
	CANCELLED;

	public boolean isTerminal() {
		return this == SUCCEEDED || this == CANCELLED;
	}

	public boolean isCancellable() {
		return this == PENDING || this == RUNNING;
	}
}
