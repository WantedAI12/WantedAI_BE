package com.perfumeryaicore.global.common;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 제품 종류. 조향 AI가 허용하는 8종으로 변환한다.
 */
@Getter
@RequiredArgsConstructor
public enum ProductCategory {

	EAU_DE_PARFUM("eau_de_parfum"),
	EAU_DE_TOILETTE("eau_de_toilette"),
	EAU_DE_COLOGNE("eau_de_cologne"),
	SHAMPOO("shampoo"),
	BODY_WASH("body_wash"),
	CANDLE("candle"),
	ROOM_SPRAY("room_spray"),
	DIFFUSER("diffuser"),
	/**
	 * 표준 조향 AI 계약({@code /v1/formulas})의 8종과 달리, 이 카테고리는 별도 엔드포인트
	 * ({@code /v1/applications/body-lotion/design})로 라우팅되는 신호로만 쓰인다 -
	 * {@code modalValue}가 표준 요청의 {@code product_category} 필드로 나가지 않는다.
	 */
	BODY_LOTION("body_lotion");

	/** 조향 AI({@code product_category}) 전달 값. */
	private final String modalValue;

	public static ProductCategory fromModalValue(String value) {
		return Arrays.stream(values())
				.filter(c -> c.modalValue.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("알 수 없는 product_category: " + value));
	}
}
