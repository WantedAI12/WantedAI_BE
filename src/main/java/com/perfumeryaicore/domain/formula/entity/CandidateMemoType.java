package com.perfumeryaicore.domain.formula.entity;

/** 후보에 남길 수 있는 메모 3종. 후보당 타입별로 최대 1건만 존재한다. */
public enum CandidateMemoType {
	/** 입력내용 — 이 후보를 만들 때 참고한 입력/전제를 정리한 메모. */
	INPUT_NOTE,
	/** 검토사항 — 리뷰 중 발견한 문제·의견. */
	REVIEW_NOTE,
	/** 다음실험 — 다음 실험에서 확인할 항목. */
	NEXT_EXPERIMENT_NOTE
}
