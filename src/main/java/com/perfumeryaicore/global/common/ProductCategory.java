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

	/** 향료 농도로 서로 구분되는 향수 3종(오 드 코롱·뚜왈렛·퍼퓸)이면 {@code true}. */
	public boolean isFragranceGrade() {
		return this == EAU_DE_PARFUM || this == EAU_DE_TOILETTE || this == EAU_DE_COLOGNE;
	}

	/**
	 * 향수 제품 유형을 사용 농도(향료 w/w %)로 정한다 - 통상 기준의 반개구간이다:
	 * 5% 미만 오 드 코롱, 5% 이상 15% 미만 오 드 뚜왈렛, 15% 이상 오 드 퍼퓸.
	 *
	 * <p>15%를 오 드 퍼퓸으로 두는 건 AI 기본값(농도 15%, 제품 오 드 퍼퓸)과 맞추기 위해서다. 20%를
	 * 넘는 퍼퓸·엑스트레는 AI가 별도 유형으로 받지 않아 오 드 퍼퓸에 포함한다. 조향 AI에는 이런 농도별
	 * 제품 규칙이 없어(제품 유형과 농도를 별개 값으로 받는다) 우리 쪽에서 정하며, 기준을 바꿀 때는 이
	 * 메서드만 고치면 된다(2026-09-19 결정).
	 */
	public static ProductCategory forFragranceConcentration(double percent) {
		if (percent < 5.0) {
			return EAU_DE_COLOGNE;
		}
		if (percent < 15.0) {
			return EAU_DE_TOILETTE;
		}
		return EAU_DE_PARFUM;
	}

	public static ProductCategory fromModalValue(String value) {
		return Arrays.stream(values())
				.filter(c -> c.modalValue.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("알 수 없는 product_category: " + value));
	}
}
