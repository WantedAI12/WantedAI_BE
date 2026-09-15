package com.perfumeryaicore.domain.request.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 체크리스트 항목 담당자 배정/해제. {@code assigneeId}가 {@code null}이면 배정 해제다.
 * {@code expectedRevision}은 완료 상태 변경과 같은 낙관적 잠금을 공유한다.
 */
public record AssignWorkChecklistItemRequest(

		Long assigneeId,

		@NotNull
		@PositiveOrZero
		Integer expectedRevision
) {
}
