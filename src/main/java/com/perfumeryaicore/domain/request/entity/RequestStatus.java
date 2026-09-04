package com.perfumeryaicore.domain.request.entity;

/**
 * 자연어 향 요청의 상태.
 *
 * <pre>
 *   DRAFT ─(필수값 채워짐)─▶ DRAFT ─confirm─▶ CONFIRMED
 *     │                                         │
 *     └─(필수값 누락)─▶ MISSING_FIELDS           └─▶ 후보 생성 가능
 *   BLOCKED : 값 모순 등으로 진행 불가
 * </pre>
 */
public enum RequestStatus {

	/** 편집 중. 필수값이 모두 채워졌고 확정만 남은 상태. */
	DRAFT,
	/** 필수 항목 누락. 확정 불가. */
	MISSING_FIELDS,
	/** 확정됨. 후보 조향식 생성 가능. */
	CONFIRMED,
	/** 값 모순 등으로 진행 차단. */
	BLOCKED;

	public boolean isEditable() {
		return this != CONFIRMED;
	}
}
