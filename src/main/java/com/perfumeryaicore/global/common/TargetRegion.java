package com.perfumeryaicore.global.common;

/**
 * 대상 시장. 조향 AI가 허용하는 세 지역으로 정규화한다.
 */
public enum TargetRegion {

	KR,
	EU,
	US;

	/** 조향 AI({@code target_region}) 전달 값. */
	public String modalValue() {
		return name();
	}
}
