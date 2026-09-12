package com.perfumeryaicore.domain.formula.dto.request;

import com.perfumeryaicore.domain.formula.entity.CandidateMemo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 메모 저장(생성·수정 겸용) 요청. {@code expectedRevision}은 클라이언트가 화면에 마지막으로 띄웠던
 * revision — 처음 저장하는 메모라면 0을 보낸다. 서버에 저장된 값과 다르면 409로 거부된다.
 */
public record UpsertCandidateMemoRequest(

		@NotNull
		@Size(max = CandidateMemo.CONTENT_MAX)
		String content,

		@NotNull
		@PositiveOrZero
		Integer expectedRevision
) {
}
