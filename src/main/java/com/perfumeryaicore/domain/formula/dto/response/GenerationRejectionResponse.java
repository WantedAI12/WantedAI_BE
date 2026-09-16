package com.perfumeryaicore.domain.formula.dto.response;

import com.perfumeryaicore.domain.formula.entity.GenerationRejection;
import java.time.LocalDateTime;
import tools.jackson.databind.JsonNode;

/**
 * 기권(no_safe_match)한 생성 시도 한 건. 후보가 아니다 - 정상 추천 후보 목록에 섞지 않는다.
 *
 * @param diagnostics AI 응답 원문을 파싱한 진단 데이터(근접 후보·매칭 점수 등). 파싱에 실패하면 {@code null}
 */
public record GenerationRejectionResponse(
		Long id,
		Long requestId,
		Long jobId,
		String reasonCode,
		String message,
		JsonNode diagnostics,
		LocalDateTime createdAt
) {

	public static GenerationRejectionResponse of(GenerationRejection rejection, JsonNode diagnostics) {
		return new GenerationRejectionResponse(
				rejection.getId(),
				rejection.getRequestId(),
				rejection.getJobId(),
				rejection.getReasonCode(),
				rejection.getMessage(),
				diagnostics,
				rejection.getCreatedAt());
	}
}
