package com.perfumeryaicore.domain.experiment.dto.request;

import com.perfumeryaicore.global.common.CandidateStatus;
import jakarta.validation.constraints.NotNull;

public record ExperimentStatusChangeRequest(

		@NotNull
		CandidateStatus status
) {
}
