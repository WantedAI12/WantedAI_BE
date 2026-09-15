package com.perfumeryaicore.global.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code assess-evidence} 응답의 차단 사유 한 건. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvidenceBlocker(

		@JsonProperty("ingredient_id")
		String ingredientId,

		String reason
) {
}
