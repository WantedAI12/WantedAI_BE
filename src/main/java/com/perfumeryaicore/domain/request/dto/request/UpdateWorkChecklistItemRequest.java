package com.perfumeryaicore.domain.request.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 체크리스트 항목 완료/해제. {@code expectedRevision}은 클라이언트가 마지막으로 읽은 revision —
 * 서버에 저장된 값과 다르면 409로 거부된다.
 */
public record UpdateWorkChecklistItemRequest(

		@NotNull
		Boolean completed,

		@NotNull
		@PositiveOrZero
		Integer expectedRevision
) {
}
