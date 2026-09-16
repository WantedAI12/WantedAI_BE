package com.perfumeryaicore.global.common;

/**
 * 후보 조향식의 실험 워크플로 상태. formula(소유 엔티티)와 experiment(상태 전이·이력) 양쪽이
 * 공유하는 값 타입이라 {@code global.common}에 둔다({@code ProductCategory}/{@code TargetRegion}과 동일한 이유).
 *
 * <pre>
 *   UNDER_REVIEW ─▶ CONFIRMED_FOR_EXPERIMENT ─▶ IN_SENSORY_TEST ─▶ APPROVED
 *        │                  │                        │
 *        └──────────────────┴────────────────────────┴──▶ REJECTED
 * </pre>
 */
public enum CandidateStatus {
	UNDER_REVIEW,
	CONFIRMED_FOR_EXPERIMENT,
	IN_SENSORY_TEST,
	APPROVED,
	REJECTED
}
