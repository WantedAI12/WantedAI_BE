package com.perfumeryaicore.domain.request.dto.response;

import com.perfumeryaicore.domain.request.entity.WorkChecklistItemType;

/** 허브 화면의 "내 체크리스트 목록" 한 줄 - 미완료 항목만 담긴다. */
public record HubChecklistItemResponse(
		Long projectId,
		Long requestId,
		WorkChecklistItemType itemType
) {
}
