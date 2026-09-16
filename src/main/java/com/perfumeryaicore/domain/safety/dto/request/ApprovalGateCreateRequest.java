package com.perfumeryaicore.domain.safety.dto.request;

import com.perfumeryaicore.domain.safety.entity.ApprovalDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApprovalGateCreateRequest(

		@NotNull
		ApprovalDecision decision,

		@Size(max = 1000)
		String comment
) {
}
