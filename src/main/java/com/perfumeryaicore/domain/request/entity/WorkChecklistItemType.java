package com.perfumeryaicore.domain.request.entity;

/**
 * 향수 작업({@link FragranceRequest}) 체크리스트의 고정 6단계(BE-090~098).
 * 항목 추가/삭제(커스터마이즈)는 아직 정책이 정해지지 않아 다루지 않는다.
 */
public enum WorkChecklistItemType {
	FRAGRANCE_BRIEF,
	CANDIDATE_REVIEW,
	SAFETY_REVIEW,
	TESTING,
	SENSORY_EVALUATION,
	FINAL_CONFIRMATION
}
