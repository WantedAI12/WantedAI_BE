package com.perfumeryaicore.domain.formula.dto.request;

import jakarta.validation.constraints.Size;

public record DuplicateCandidateRequest(

		@Size(max = 1000)
		String reason
) {
}
