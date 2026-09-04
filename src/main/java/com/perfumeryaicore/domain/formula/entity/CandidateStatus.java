package com.perfumeryaicore.domain.formula.entity;

/**
 * 후보 조향식의 실험 워크플로 상태.
 *
 * <pre>
 *   UNDER_REVIEW ─▶ CONFIRMED_FOR_EXPERIMENT ─▶ IN_SENSORY_TEST ─▶ APPROVED / REJECTED
 * </pre>
 */
public enum CandidateStatus {
	UNDER_REVIEW,
	CONFIRMED_FOR_EXPERIMENT,
	IN_SENSORY_TEST,
	APPROVED,
	REJECTED
}
