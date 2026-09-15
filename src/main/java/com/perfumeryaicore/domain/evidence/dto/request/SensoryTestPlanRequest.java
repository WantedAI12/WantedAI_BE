package com.perfumeryaicore.domain.evidence.dto.request;

import com.perfumeryaicore.domain.evidence.entity.BlindLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SensoryTestPlanRequest(

		@NotBlank
		@Size(max = 2000)
		String planDetail,

		@Size(max = 50)
		String protocolVersion,

		@Size(max = 50)
		String sampleCode,

		@Size(max = 50)
		String batchLot,

		@Min(1)
		Integer panelSize,

		BlindLevel blindLevel
) {
}
