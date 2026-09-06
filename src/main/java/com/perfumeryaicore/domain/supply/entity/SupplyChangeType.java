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
	OTHER
}
