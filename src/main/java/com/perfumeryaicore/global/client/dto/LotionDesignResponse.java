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
	 * 후보로 저장해도 되는지. AI팀 확인(2026-09-17): 목표 유사도 미달({@code profile_target_met}
	 * false)도 유효한 계산 결과이지 기권이 아니다 - 실제 배합(recipe)이 있는지만으로 판단하고,
	 * 목표 달성 여부는 별도로 보관해 화면에서 구분해 보여준다(통과·승인으로 바뀌는 게 아님).
	 * recipe가 비어있고 closest_candidate만 있는 경우(기권·탐색 미완료)만 저장하지 않는다.
	 */
	public boolean isUsableCandidate() {
		return recipe != null && !recipe.isEmpty();
	}

	public int recipeSize() {
		return recipe == null ? 0 : recipe.size();
	}
}
