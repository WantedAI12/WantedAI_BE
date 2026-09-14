package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modal {@code POST /v1/applications/body-lotion/design} 요청 본문.
 *
 * <p>1단계(팀 확인 완료): 중첩 옵션 6종({@code storage}/{@code skin_exposure}/
 * {@code transition_schedule}/{@code base_design}/{@code dose_trials}/{@code process})은 이번
 * 버전에 넣지 않는다 - 기본 참조 로션·기본 계산 조건을 그대로 쓴다는 뜻이다. 생략해도
 * Modal이 알아서 기본 객체를 적용하며(예: skin_exposure는 피부 유형 all·면적 1000cm²·
 * 체중 60kg·1일 1회 적용), 실제 적용된 값은 응답 원문에 그대로 남으므로 원문 보존으로
 * 화면에서 확인 가능하다(read-time 매퍼는 후속 배치).
 *
 * <p>{@code fragrance_concentration_percent}(0.01~3.0) 등 로션 전용 스칼라 필드는 아직
 * {@code FragranceRequest}에 대응 컬럼이 없어 이번 배치에선 보내지 않는다(Modal 기본값 사용) -
 * 표준 향수의 {@code usageConcentrationPercent}(0~30)와 유효 범위가 달라 그대로 재사용하면
 * 안 된다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LotionEstimateRequest(

		@JsonProperty("brief")
		String brief,

		@JsonProperty("max_risk_tier")
		Integer maxRiskTier,

		@JsonProperty("max_ingredient_price_per_kg")
		Double maxIngredientPricePerKg
) {

	public static LotionEstimateRequest of(String brief, Integer maxRiskTier, Double maxIngredientPricePerKg) {
		return new LotionEstimateRequest(brief, maxRiskTier, maxIngredientPricePerKg);
	}
}
