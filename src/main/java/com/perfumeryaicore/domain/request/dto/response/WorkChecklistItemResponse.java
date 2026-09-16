package com.perfumeryaicore.domain.request.dto.response;

import com.perfumeryaicore.domain.request.entity.WorkChecklistItem;
import com.perfumeryaicore.domain.request.entity.WorkChecklistItemType;
import java.time.LocalDateTime;

public record WorkChecklistItemResponse(
		WorkChecklistItemType itemType,
		boolean completed,
		LocalDateTime completedAt,
		Long completedBy,
		Long assignedTo,
		Long assignedBy,
		LocalDateTime assignedAt,
		int revision
) {

	public static WorkChecklistItemResponse from(WorkChecklistItem item) {
		return new WorkChecklistItemResponse(
				item.getItemType(),
				item.isCompleted(),
				item.getCompletedAt(),
				item.getCompletedBy(),
				item.getAssignedTo(),
				item.getAssignedBy(),
				item.getAssignedAt(),
				item.getRevision());
	}
}
