package com.perfumeryaicore.domain.formula.dto.request;

import jakarta.validation.constraints.NotNull;

public record RestoreCandidateVersionRequest(

		@NotNull
		Long versionId
) {
}
