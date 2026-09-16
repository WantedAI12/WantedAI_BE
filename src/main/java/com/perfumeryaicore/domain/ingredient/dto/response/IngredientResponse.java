package com.perfumeryaicore.domain.ingredient.dto.response;

import java.time.LocalDateTime;

/**
 * 로컬에서 관측된 원료 한 건. 조향 AI가 원료 목록을 제공하지 않으므로, 실제 생성된 조향식
 * ({@code candidate_version_ingredients})에 등장한 원료를 외부 식별자 기준으로 중복 제거해 보여준다.
 *
 * @param ingredientId          조향 AI가 준 외부 식별자 (예: {@code dihydromyrcenol})
 * @param name                  가장 최근 버전에서의 표시 이름
 * @param pyramid               top/heart/base 등 (가장 최근 관측값)
 * @param pricePerKg            가장 최근 관측 단가 (USD/kg 가정, AI 응답값 그대로)
 * @param availability          가장 최근 관측 가용성 지표 (0~1, AI 응답값 그대로)
 * @param usedInCandidateCount  이 원료를 쓰는 후보 수 (요청자 접근 가능 프로젝트 범위)
 * @param lastSeenAt            이 원료가 마지막으로 관측된 후보 버전 생성 시각
 */
public record IngredientResponse(
		String ingredientId,
		String name,
		String pyramid,
		Double pricePerKg,
		Double availability,
		int usedInCandidateCount,
		LocalDateTime lastSeenAt
) {
}
