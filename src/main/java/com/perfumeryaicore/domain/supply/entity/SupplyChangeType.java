package com.perfumeryaicore.domain.supply.entity;

/**
 * 원료 공급 조건 변경 유형.
 */
public enum SupplyChangeType {

	PRICE_INCREASE,
	PRICE_DECREASE,
	DISCONTINUED,
	LEAD_TIME_INCREASE,
	SUPPLY_RESTORED,
	/** 안전·규제 상태 변경(BE-070) - 예: IFRA 제한 등급 변경, 규제 승인 철회. */
	SAFETY_REGULATORY_CHANGE,
	/** 원료 식별 정보 변경(BE-070) - 예: CAS 번호 정정, 공급사 변경. */
	IDENTITY_CHANGE,
	/** 재고·MOQ 변경(BE-070) - 가격 외 공급 조건. */
	SUPPLY_TERMS_CHANGE,
	OTHER
}
