package com.perfumeryaicore.domain.experiment.dto.request;

import com.perfumeryaicore.global.common.CandidateStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExperimentStatusChangeRequest(

		@NotNull
		CandidateStatus status,

		@Size(max = 1000)
		String reason
) {
}
