package com.perfumeryaicore.domain.evidence.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SensoryTestPlanRequest(

		@NotBlank
		@Size(max = 2000)
		String planDetail
) {
}
