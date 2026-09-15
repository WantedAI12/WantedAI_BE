package com.perfumeryaicore.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** 페이지네이션이 적용된 목록 응답의 공통 포맷. */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean hasNext) {

	public static <T> PageResponse<T> of(Page<T> page) {
		return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
				page.getTotalElements(), page.getTotalPages(), page.hasNext());
	}
}
