package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Modal {@code POST /v1/applications/body-lotion/design} 응답에서 후보 생성 판정에 필요한
 * 최소 필드만 추린 뷰. 전체 원문은 100줄이 넘고(제품 모델·지각 모델 메타데이터 등) 화면에
 * 필요한 나머지 필드는 저장된 원문을 읽어 뽑는 read-time 매퍼로 후속 배치에서 처리한다
 * (formula 도메인의 {@code PredictionMapper}와 같은 패턴).
 *
 * <p>{@code score}를 숫자로 단정하지 않고 {@link JsonNode}로 받는다 - {@code confidence}
 * 필드가 항상 숫자라고 가정했다가 겪은 파싱 실패를 반복하지 않기 위함이다. {@code recipe}/
 * {@code closestCandidate} 원소도 마찬가지로 실제 성공 응답 예시를 아직 확인하지 못해
 * 필드 구조를 단정하지 않고 원문 노드 그대로 받는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LotionDesignResponse(

		@JsonProperty("status")
		String status,

		@JsonProperty("profile_target_met")
		Boolean profileTargetMet,

		@JsonProperty("search_incomplete")
		Boolean searchIncomplete,

		@JsonProperty("score")
		JsonNode score,

		@JsonProperty("recipe")
		List<JsonNode> recipe,

		@JsonProperty("closest_candidate")
		List<JsonNode> closestCandidate
) {

	/**
	 * 정상 추천 후보로 저장해도 되는지. {@code profile_target_met}이 참이고 실제 배합이
	 * 있어야 한다 - 팀 확인: recipe가 비어있거나 closest_candidate만 있는 경우(기권·탐색
	 * 미완료)를 정상 후보로 저장·노출하면 안 된다.
	 */
	public boolean isUsableCandidate() {
		return Boolean.TRUE.equals(profileTargetMet) && recipe != null && !recipe.isEmpty();
	}

	public int recipeSize() {
		return recipe == null ? 0 : recipe.size();
	}
}
