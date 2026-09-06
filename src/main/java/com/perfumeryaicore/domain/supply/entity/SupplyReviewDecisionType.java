package com.perfumeryaicore.domain.supply.entity;

/**
 * 공급 변경 영향을 받은 후보에 대한 재검토 후속 결정.
 */
public enum SupplyReviewDecisionType {

	/** 조향식 그대로 유지 */
	KEEP_FORMULA,
	/** 조향식 수정 필요 (새 버전 생성 예정) */
	REVISE_FORMULA,
	/** 후보 폐기 */
	DISCARD_CANDIDATE
}
