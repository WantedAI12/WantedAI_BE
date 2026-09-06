package com.perfumeryaicore.global.common;

/**
 * 프로젝트(테넌트) 내 역할. 모든 하위 도메인의 역할 기반 접근 제어에 사용된다.
 *
 * <p>API 명세서 §0의 9종 역할과 1:1로 대응한다. 프로젝트를 생성한 사람이 자동으로
 * {@link #ORG_ADMIN}이 되며, 조직(Organization) 개념은 별도 엔티티 없이 프로젝트 단위로 축약한다.
 */
public enum ProjectRole {

	/** 조직 관리자. 프로젝트 생성자에게 자동 부여, 멤버/역할 전권. */
	ORG_ADMIN,
	/** 프로젝트 관리자. 멤버 초대·역할 변경·프로젝트 정보 수정. */
	PROJECT_MANAGER,
	/** 조향사. */
	PERFUMER,
	/** 향료 연구개발 담당자. */
	FRAGRANCE_RND,
	/** 제품개발·브랜드 담당자. */
	PRODUCT_BRAND,
	/** 감각과학 담당자. */
	SENSORY_SCIENTIST,
	/** 안전·규제 검토자. */
	SAFETY_REVIEWER,
	/** 향료·원료 공급업체 담당자. */
	SUPPLIER,
	/** 감사·읽기전용 검토자. */
	AUDITOR;

	/** 멤버/역할/프로젝트 정보를 관리할 수 있는 역할인지. */
	public boolean canManageProject() {
		return this == ORG_ADMIN || this == PROJECT_MANAGER;
	}
}
