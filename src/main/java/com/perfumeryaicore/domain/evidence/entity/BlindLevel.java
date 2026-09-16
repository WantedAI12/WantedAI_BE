package com.perfumeryaicore.domain.evidence.entity;

/** 블라인드 수준(BE-054). */
public enum BlindLevel {
	/** 평가자만 처방을 모른다. */
	SINGLE_BLIND,
	/** 평가자와 결과 등록자 모두 시료-처방 대응을 모른 채 진행한다. */
	DOUBLE_BLIND
}
