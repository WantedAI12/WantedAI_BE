package com.perfumeryaicore.domain.ingredient.dto.response;

import java.util.List;

/**
 * 원료 상세. AI 카탈로그가 원료별 안전·tier 데이터를 행 단위로 주지 않으므로,
 * 관측 가능한 값(단가·가용성·피라미드)과 이 원료를 사용하는 후보 목록만 제공한다.
 */
public record IngredientDetailResponse(
		IngredientResponse ingredient,
		List<Long> usedByCandidateIds
) {
}
